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
            'product' => 'mydealer',
            'statistics' => [
                'users_total' => DB::table('users')->count(),
                'buyers' => DB::table('users')->where('role', 'buyer')->count(),
                'vendors' => DB::table('users')->where('role', 'vendor')->count(),
                'users_blocked' => DB::table('users')->where('status', 'blocked')->count(),
                'products_total' => DB::table('products')->count(),
                'products_moderation' => DB::table('products')->where('status', 'moderation')->count(),
                'products_published' => DB::table('products')->where('status', 'published')->count(),
                'orders_total' => DB::table('orders')->count(),
                'orders_active' => DB::table('orders')->whereIn('status', ['new', 'confirmed'])->count(),
                'messages_total' => DB::table('order_messages')->count(),
            ],
            'recent_products' => $this->productQuery()->limit(8)->get()->map(function ($product): array {
                return $this->productPayload($product);
            })->values(),
            'recent_orders' => $this->orderQuery()->limit(8)->get()->map(function ($order): array {
                return $this->orderPayload($order);
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

    public function products()
    {
        return response()->json([
            'products' => $this->productQuery()->get()->map(function ($product): array {
                return $this->productPayload($product);
            })->values(),
        ]);
    }

    public function updateProductStatus(Request $request, int $productId)
    {
        $validator = Validator::make($request->all(), [
            'status' => ['required', Rule::in(['moderation', 'published', 'rejected'])],
        ]);

        if ($validator->fails()) {
            return $this->validationError($validator->errors()->toArray());
        }

        if (!DB::table('products')->where('id', $productId)->exists()) {
            return $this->notFound();
        }

        DB::table('products')->where('id', $productId)->update([
            'status' => (string) $request->input('status'),
            'updated_at' => now(),
        ]);

        $product = $this->productQuery()->where('products.id', $productId)->first();

        return response()->json([
            'message' => 'Product moderation status updated.',
            'product' => $this->productPayload($product),
        ]);
    }

    public function orders()
    {
        return response()->json([
            'orders' => $this->orderQuery()->get()->map(function ($order): array {
                return $this->orderPayload($order);
            })->values(),
        ]);
    }

    public function updateOrderStatus(Request $request, int $orderId)
    {
        $validator = Validator::make($request->all(), [
            'status' => ['required', Rule::in(['new', 'confirmed', 'completed', 'cancelled'])],
        ]);

        if ($validator->fails()) {
            return $this->validationError($validator->errors()->toArray());
        }

        if (!DB::table('orders')->where('id', $orderId)->exists()) {
            return $this->notFound();
        }

        DB::table('orders')->where('id', $orderId)->update([
            'status' => (string) $request->input('status'),
            'updated_at' => now(),
        ]);

        $order = $this->orderQuery()->where('orders.id', $orderId)->first();

        return response()->json([
            'message' => 'Order status updated by administrator.',
            'order' => $this->orderPayload($order),
        ]);
    }

    private function productQuery()
    {
        return DB::table('products')
            ->join('users as vendors', 'vendors.id', '=', 'products.vendor_id')
            ->select([
                'products.*',
                'vendors.name as vendor_name',
                'vendors.email as vendor_email',
            ])
            ->orderByDesc('products.created_at');
    }

    private function orderQuery()
    {
        return DB::table('orders')
            ->join('users as buyers', 'buyers.id', '=', 'orders.buyer_id')
            ->join('users as vendors', 'vendors.id', '=', 'orders.vendor_id')
            ->select([
                'orders.*',
                'buyers.name as buyer_name',
                'buyers.email as buyer_email',
                'vendors.name as vendor_name',
                'vendors.email as vendor_email',
            ])
            ->orderByDesc('orders.created_at');
    }

    private function productPayload($product): array
    {
        return [
            'id' => (int) $product->id,
            'name' => (string) $product->name,
            'category' => (string) $product->category,
            'price' => (float) $product->price,
            'unit' => (string) $product->unit,
            'emoji' => (string) $product->emoji,
            'status' => (string) $product->status,
            'vendor_name' => (string) $product->vendor_name,
            'vendor_email' => (string) $product->vendor_email,
            'created_at' => (string) $product->created_at,
            'updated_at' => (string) $product->updated_at,
        ];
    }

    private function orderPayload($order): array
    {
        return [
            'id' => (int) $order->id,
            'total' => (float) $order->total,
            'status' => (string) $order->status,
            'buyer_name' => (string) $order->buyer_name,
            'buyer_email' => (string) $order->buyer_email,
            'vendor_name' => (string) $order->vendor_name,
            'vendor_email' => (string) $order->vendor_email,
            'created_at' => (string) $order->created_at,
            'updated_at' => (string) $order->updated_at,
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
