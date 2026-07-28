<?php

namespace App\Http\Controllers\Api;

use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Validator;
use Illuminate\Validation\Rule;

class TaskController
{
    public function index(Request $request)
    {
        $user = $request->user();
        $query = DB::table('tasks')->orderByDesc('created_at');

        if ($user->role === 'customer') {
            $query->where('customer_id', $user->id);
        } else {
            $query->where(function ($nested) use ($user): void {
                $nested->where('status', 'open')
                    ->orWhereExists(function ($offerQuery) use ($user): void {
                        $offerQuery->select(DB::raw(1))
                            ->from('task_offers')
                            ->whereColumn('task_offers.task_id', 'tasks.id')
                            ->where('task_offers.contractor_id', $user->id);
                    });
            });
        }

        $tasks = $query->get()->map(function ($task) {
            return $this->taskPayload($task);
        })->values();

        return response()->json(['tasks' => $tasks]);
    }

    public function show(Request $request, int $taskId)
    {
        $task = $this->visibleTask($request, $taskId);

        if (!$task) {
            return $this->notFound();
        }

        return response()->json(['task' => $this->taskPayload($task)]);
    }

    public function store(Request $request)
    {
        $user = $request->user();

        if ($user->role !== 'customer') {
            return $this->forbidden('Only customers can create tasks.');
        }

        $validator = Validator::make($request->all(), [
            'title' => ['required', 'string', 'min:3', 'max:160'],
            'category' => ['required', 'string', 'max:80'],
            'address' => ['required', 'string', 'max:190'],
            'description' => ['required', 'string', 'min:3', 'max:3000'],
            'budget' => ['required', 'numeric', 'min:1', 'max:999999'],
        ]);

        if ($validator->fails()) {
            return $this->validationError($validator->errors()->toArray());
        }

        $now = now();
        $taskId = DB::table('tasks')->insertGetId([
            'customer_id' => $user->id,
            'title' => trim((string) $request->input('title')),
            'category' => trim((string) $request->input('category')),
            'address' => trim((string) $request->input('address')),
            'description' => trim((string) $request->input('description')),
            'budget' => (float) $request->input('budget'),
            'status' => 'open',
            'selected_offer_id' => null,
            'created_at' => $now,
            'updated_at' => $now,
        ]);

        $task = DB::table('tasks')->where('id', $taskId)->first();

        return response()->json([
            'message' => 'Task created.',
            'task' => $this->taskPayload($task),
        ], 201);
    }

    public function createOffer(Request $request, int $taskId)
    {
        $user = $request->user();

        if ($user->role !== 'contractor') {
            return $this->forbidden('Only contractors can create offers.');
        }

        $task = DB::table('tasks')->where('id', $taskId)->first();

        if (!$task || $task->status !== 'open') {
            return $this->notFound();
        }

        $validator = Validator::make($request->all(), [
            'price' => ['required', 'numeric', 'min:1', 'max:999999'],
            'distance' => ['nullable', 'string', 'max:40'],
        ]);

        if ($validator->fails()) {
            return $this->validationError($validator->errors()->toArray());
        }

        $existing = DB::table('task_offers')
            ->where('task_id', $taskId)
            ->where('contractor_id', $user->id)
            ->first();

        if ($existing) {
            return response()->json([
                'message' => 'Offer already exists.',
                'code' => 'OFFER_ALREADY_EXISTS',
            ], 409);
        }

        $offerId = DB::table('task_offers')->insertGetId([
            'task_id' => $taskId,
            'contractor_id' => $user->id,
            'price' => (float) $request->input('price'),
            'rating' => 4.80,
            'distance' => trim((string) $request->input('distance', '2,0 км')),
            'status' => 'pending',
            'created_at' => now(),
            'updated_at' => now(),
        ]);

        $offer = DB::table('task_offers')->where('id', $offerId)->first();

        return response()->json([
            'message' => 'Offer created.',
            'offer' => $this->offerPayload($offer),
        ], 201);
    }

    public function selectOffer(Request $request, int $taskId, int $offerId)
    {
        $user = $request->user();
        $task = DB::table('tasks')->where('id', $taskId)->first();

        if (!$task || (int) $task->customer_id !== (int) $user->id || $user->role !== 'customer') {
            return $this->notFound();
        }

        if ($task->status !== 'open') {
            return response()->json([
                'message' => 'Task no longer accepts offers.',
                'code' => 'TASK_NOT_OPEN',
            ], 409);
        }

        $offer = DB::table('task_offers')
            ->where('id', $offerId)
            ->where('task_id', $taskId)
            ->first();

        if (!$offer) {
            return $this->notFound();
        }

        DB::transaction(function () use ($taskId, $offerId): void {
            DB::table('task_offers')->where('task_id', $taskId)->update([
                'status' => 'rejected',
                'updated_at' => now(),
            ]);

            DB::table('task_offers')->where('id', $offerId)->update([
                'status' => 'selected',
                'updated_at' => now(),
            ]);

            DB::table('tasks')->where('id', $taskId)->update([
                'status' => 'assigned',
                'selected_offer_id' => $offerId,
                'updated_at' => now(),
            ]);
        });

        $updated = DB::table('tasks')->where('id', $taskId)->first();

        return response()->json([
            'message' => 'Offer selected.',
            'task' => $this->taskPayload($updated),
        ]);
    }

    public function updateStatus(Request $request, int $taskId)
    {
        $validator = Validator::make($request->all(), [
            'status' => ['required', Rule::in(['completed'])],
        ]);

        if ($validator->fails()) {
            return $this->validationError($validator->errors()->toArray());
        }

        $task = DB::table('tasks')->where('id', $taskId)->first();

        if (!$task || !$this->canCompleteTask($request->user(), $task)) {
            return $this->notFound();
        }

        if ($task->status !== 'assigned') {
            return response()->json([
                'message' => 'Only assigned tasks can be completed.',
                'code' => 'TASK_STATUS_INVALID',
            ], 409);
        }

        DB::table('tasks')->where('id', $taskId)->update([
            'status' => (string) $request->input('status'),
            'updated_at' => now(),
        ]);

        return response()->json([
            'message' => 'Task status updated.',
            'task' => $this->taskPayload(DB::table('tasks')->where('id', $taskId)->first()),
        ]);
    }

    public function messages(Request $request, int $taskId)
    {
        $task = DB::table('tasks')->where('id', $taskId)->first();

        if (!$task || !$this->isParticipant($request->user(), $task)) {
            return $this->notFound();
        }

        $messages = DB::table('task_messages')
            ->join('users', 'users.id', '=', 'task_messages.author_id')
            ->where('task_messages.task_id', $taskId)
            ->orderBy('task_messages.created_at')
            ->get([
                'task_messages.id',
                'task_messages.task_id',
                'task_messages.author_id',
                'task_messages.text',
                'task_messages.created_at',
                'users.name as author_name',
            ])
            ->map(function ($message): array {
                return [
                    'id' => (int) $message->id,
                    'taskId' => (int) $message->task_id,
                    'authorId' => (int) $message->author_id,
                    'authorName' => (string) $message->author_name,
                    'text' => (string) $message->text,
                    'createdAt' => (string) $message->created_at,
                ];
            })->values();

        return response()->json([
            'task' => $this->taskPayload($task),
            'messages' => $messages,
        ]);
    }

    public function sendMessage(Request $request, int $taskId)
    {
        $task = DB::table('tasks')->where('id', $taskId)->first();

        if (!$task || !$this->isParticipant($request->user(), $task)) {
            return $this->notFound();
        }

        $validator = Validator::make($request->all(), [
            'text' => ['required', 'string', 'min:1', 'max:2000'],
        ]);

        if ($validator->fails()) {
            return $this->validationError($validator->errors()->toArray());
        }

        $messageId = DB::table('task_messages')->insertGetId([
            'task_id' => $taskId,
            'author_id' => $request->user()->id,
            'text' => trim((string) $request->input('text')),
            'created_at' => now(),
            'updated_at' => now(),
        ]);

        return response()->json([
            'message' => 'Message created.',
            'message_id' => $messageId,
        ], 201);
    }

    private function visibleTask(Request $request, int $taskId)
    {
        $task = DB::table('tasks')->where('id', $taskId)->first();

        if (!$task) {
            return null;
        }

        $user = $request->user();

        if ($user->role === 'customer') {
            return (int) $task->customer_id === (int) $user->id ? $task : null;
        }

        if ($user->role === 'contractor') {
            if ($task->status === 'open') {
                return $task;
            }

            $hasOffer = DB::table('task_offers')
                ->where('task_id', $taskId)
                ->where('contractor_id', $user->id)
                ->exists();

            return $hasOffer ? $task : null;
        }

        return null;
    }

    private function isParticipant($user, $task): bool
    {
        if ($user->role === 'customer') {
            return (int) $task->customer_id === (int) $user->id;
        }

        if ($user->role !== 'contractor') {
            return false;
        }

        return DB::table('task_offers')
            ->where('task_id', $task->id)
            ->where('contractor_id', $user->id)
            ->exists();
    }


    private function canCompleteTask($user, $task): bool
    {
        if ($user->role === 'customer') {
            return (int) $task->customer_id === (int) $user->id;
        }

        if ($user->role !== 'contractor' || !$task->selected_offer_id) {
            return false;
        }

        return DB::table('task_offers')
            ->where('id', $task->selected_offer_id)
            ->where('task_id', $task->id)
            ->where('contractor_id', $user->id)
            ->where('status', 'selected')
            ->exists();
    }

    private function taskPayload($task): array
    {
        $customer = DB::table('users')->where('id', $task->customer_id)->first();
        $offers = DB::table('task_offers')
            ->where('task_id', $task->id)
            ->orderBy('price')
            ->get()
            ->map(function ($offer): array {
                return $this->offerPayload($offer);
            })->values();

        return [
            'id' => (int) $task->id,
            'title' => (string) $task->title,
            'category' => (string) $task->category,
            'address' => (string) $task->address,
            'description' => (string) $task->description,
            'budget' => (float) $task->budget,
            'status' => (string) $task->status,
            'customerId' => (int) $task->customer_id,
            'customerName' => $customer ? (string) $customer->name : 'Заказчик',
            'selectedOfferId' => $task->selected_offer_id ? (int) $task->selected_offer_id : null,
            'offers' => $offers,
            'createdAt' => (string) $task->created_at,
        ];
    }

    private function offerPayload($offer): array
    {
        $contractor = DB::table('users')->where('id', $offer->contractor_id)->first();

        return [
            'id' => (int) $offer->id,
            'taskId' => (int) $offer->task_id,
            'contractorId' => (int) $offer->contractor_id,
            'name' => $contractor ? (string) $contractor->name : 'Исполнитель',
            'price' => (float) $offer->price,
            'rating' => (float) $offer->rating,
            'distance' => (string) $offer->distance,
            'status' => (string) $offer->status,
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

    private function forbidden(string $message)
    {
        return response()->json([
            'message' => $message,
            'code' => 'ACTION_FORBIDDEN',
        ], 403);
    }

    private function notFound()
    {
        return response()->json([
            'message' => 'Task was not found.',
            'code' => 'TASK_NOT_FOUND',
        ], 404);
    }
}
