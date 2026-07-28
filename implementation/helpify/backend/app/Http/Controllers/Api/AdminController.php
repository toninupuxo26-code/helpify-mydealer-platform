<?php

namespace App\Http\Controllers\Api;

use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Validator;
use Illuminate\Validation\Rule;

class AdminController
{
    public function dashboard()
    {
        return response()->json([
            'product' => 'helpify',
            'statistics' => [
                'users_total' => DB::table('users')->count(),
                'customers' => DB::table('users')->where('role', 'customer')->count(),
                'contractors' => DB::table('users')->where('role', 'contractor')->count(),
                'users_blocked' => DB::table('users')->where('status', 'blocked')->count(),
                'tasks_total' => DB::table('tasks')->count(),
                'tasks_open' => DB::table('tasks')->where('status', 'open')->count(),
                'tasks_assigned' => DB::table('tasks')->where('status', 'assigned')->count(),
                'tasks_completed' => DB::table('tasks')->where('status', 'completed')->count(),
                'offers_total' => DB::table('task_offers')->count(),
                'messages_total' => DB::table('task_messages')->count(),
            ],
            'recent_tasks' => $this->taskQuery()->limit(8)->get()->map(function ($task): array {
                return $this->taskPayload($task);
            })->values(),
        ]);
    }

    public function users()
    {
        $users = DB::table('users')
            ->select(['id', 'name', 'email', 'role', 'status', 'created_at'])
            ->orderByDesc('id')
            ->get()
            ->map(function ($user): array {
                return [
                    'id' => (int) $user->id,
                    'name' => (string) $user->name,
                    'email' => (string) $user->email,
                    'role' => (string) $user->role,
                    'status' => (string) $user->status,
                    'created_at' => (string) $user->created_at,
                ];
            });

        return response()->json(['users' => $users]);
    }

    public function updateUserStatus(Request $request, int $userId)
    {
        $validator = Validator::make($request->all(), [
            'status' => ['required', Rule::in(['active', 'blocked'])],
        ]);

        if ($validator->fails()) {
            return $this->validationError($validator->errors()->toArray());
        }

        if ((int) $request->user()->id === $userId) {
            return response()->json([
                'message' => 'The active administrator cannot change their own status.',
                'code' => 'ADMIN_SELF_STATUS_CHANGE',
            ], 409);
        }

        $user = DB::table('users')->where('id', $userId)->first();

        if (!$user) {
            return $this->notFound();
        }

        $status = (string) $request->input('status');

        DB::transaction(function () use ($userId, $status): void {
            DB::table('users')->where('id', $userId)->update([
                'status' => $status,
                'updated_at' => now(),
            ]);

            if ($status === 'blocked') {
                DB::table('api_tokens')->where('user_id', $userId)->delete();
            }
        });

        return response()->json([
            'message' => 'User status updated.',
            'user' => DB::table('users')
                ->select(['id', 'name', 'email', 'role', 'status'])
                ->where('id', $userId)
                ->first(),
        ]);
    }

    public function tasks()
    {
        return response()->json([
            'tasks' => $this->taskQuery()->get()->map(function ($task): array {
                return $this->taskPayload($task);
            })->values(),
        ]);
    }

    public function updateTaskStatus(Request $request, int $taskId)
    {
        $validator = Validator::make($request->all(), [
            'status' => ['required', Rule::in(['open', 'completed', 'cancelled'])],
        ]);

        if ($validator->fails()) {
            return $this->validationError($validator->errors()->toArray());
        }

        $task = DB::table('tasks')->where('id', $taskId)->first();

        if (!$task) {
            return $this->notFound();
        }

        $status = (string) $request->input('status');
        $updates = [
            'status' => $status,
            'updated_at' => now(),
        ];

        if ($status === 'open') {
            $updates['selected_offer_id'] = null;
            DB::table('task_offers')->where('task_id', $taskId)->update([
                'status' => 'pending',
                'updated_at' => now(),
            ]);
        }

        DB::table('tasks')->where('id', $taskId)->update($updates);
        $updated = $this->taskQuery()->where('tasks.id', $taskId)->first();

        return response()->json([
            'message' => 'Task status updated by administrator.',
            'task' => $this->taskPayload($updated),
        ]);
    }

    private function taskQuery()
    {
        return DB::table('tasks')
            ->join('users as customers', 'customers.id', '=', 'tasks.customer_id')
            ->leftJoin('task_offers as selected_offers', 'selected_offers.id', '=', 'tasks.selected_offer_id')
            ->leftJoin('users as contractors', 'contractors.id', '=', 'selected_offers.contractor_id')
            ->select([
                'tasks.*',
                'customers.name as customer_name',
                'customers.email as customer_email',
                'contractors.name as contractor_name',
            ])
            ->orderByDesc('tasks.created_at');
    }

    private function taskPayload($task): array
    {
        return [
            'id' => (int) $task->id,
            'title' => (string) $task->title,
            'category' => (string) $task->category,
            'address' => (string) $task->address,
            'budget' => (float) $task->budget,
            'status' => (string) $task->status,
            'customer_name' => (string) $task->customer_name,
            'customer_email' => (string) $task->customer_email,
            'contractor_name' => $task->contractor_name ? (string) $task->contractor_name : null,
            'created_at' => (string) $task->created_at,
            'updated_at' => (string) $task->updated_at,
        ];
    }

    private function validationError(array $errors)
    {
        return response()->json([
            'message' => 'The submitted data is invalid.',
            'code' => 'VALIDATION_ERROR',
            'errors' => $errors,
        ], 422);
    }

    private function notFound()
    {
        return response()->json([
            'message' => 'Resource not found.',
            'code' => 'RESOURCE_NOT_FOUND',
        ], 404);
    }
}
