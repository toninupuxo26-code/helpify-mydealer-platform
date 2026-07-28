<?php

namespace App\Http\Controllers\Api;

use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Validator;
use Illuminate\Validation\Rule;

class MarketController
{
    public function products(Request $request)
    {
        $user = $request->user();
        $query = DB::table('products')
            ->leftJoin('users', 'users.id', '=', 'products.vendor_id')
            ->orderByDesc('products.created_at')
            ->select('products.*', 'users.name as vendor_name');

        if ($user->role === 'vendor') {
            $query->where('products.vendor_id', $user->id);
        } else {
            $query->where('products.status', 'published');
        }

        return response()->json([
            'products' => $query->get()->map(function ($product): array {
                return $this->productPayload($product);
            })->values(),
        ]);
    }

    public function storeProduct(Request $request)
    {
        $user = $request->user();

        if ($user->role !== 'vendor') {
            return $this->forbidden('Only vendors can create products.');
        }

        $validator = Validator::make($request->all(), [
            'name' => ['required', 'string', 'min:3', 'max:160'],
            'category' => ['required', 'string', 'max:100'],
            'price' => ['required', 'numeric', 'min:0.01', 'max:999999'],
            'unit' => ['required', 'string', 'max:60'],
            'emoji' => ['nullable', 'string', 'max:16'],
            'description' => ['required', 'string', 'min:3', 'max:3000'],
        ]);

        if ($validator->fails()) {
            return $this->validationError($validator->errors()->toArray());
        }

        $now = now();
        $productId = DB::table('products')->insertGetId([
            'vendor_id' => $user->id,
            'name' => trim((string) $request->input('name')),
            'category' => trim((string) $request->input('category')),
            'price' => (float) $request->input('price'),
            'unit' => trim((string) $request->input('unit')),
            'emoji' => trim((string) $request->input('emoji', '🌿')) ?: '🌿',
            'status' => 'moderation',
            'description' => trim((string) $request->input('description')),
            'created_at' => $now,
            'updated_at' => $now,
        ]);

        return response()->json([
            'message' => 'Product created and sent to moderation.',
            'product' => $this->findProduct($productId),
        ], 201);
    }

    public function publishProduct(Request $request, int $productId)
    {
        $user = $request->user();
        $product = DB::table('products')->where('id', $productId)->first();

        if (!$product || $user->role !== 'vendor' || (int) $product->vendor_id !== (int) $user->id) {
            return $this->notFound();
        }

        if ($product->status === 'published') {
            return response()->json([
                'message' => 'Product is already published.',
                'product' => $this->findProduct($productId),
            ]);
        }

        DB::table('products')->where('id', $productId)->update([
            'status' => 'published',
            'updated_at' => now(),
        ]);

        return response()->json([
            'message' => 'Test moderation completed.',
            'product' => $this->findProduct($productId),
        ]);
    }

    public function cart(Request $request)
    {
        if ($request->user()->role !== 'buyer') {
            return $this->forbidden('Only buyers have a cart.');
        }

        return response()->json($this->cartPayload((int) $request->user()->id));
    }

    public function addCartItem(Request $request)
    {
        $user = $request->user();

        if ($user->role !== 'buyer') {
            return $this->forbidden('Only buyers can add cart items.');
        }

        $validator = Validator::make($request->all(), [
            'product_id' => ['required', 'integer'],
            'quantity' => ['nullable', 'integer', 'min:1', 'max:99'],
        ]);

        if ($validator->fails()) {
            return $this->validationError($validator->errors()->toArray());
        }

        $productId = (int) $request->input('product_id');
        $quantity = (int) $request->input('quantity', 1);
        $product = DB::table('products')
            ->where('id', $productId)
            ->where('status', 'published')
            ->first();

        if (!$product) {
            return $this->notFound();
        }

        $existing = DB::table('cart_items')
            ->where('buyer_id', $user->id)
            ->where('product_id', $productId)
            ->first();

        if ($existing) {
            DB::table('cart_items')->where('id', $existing->id)->update([
                'quantity' => min(99, (int) $existing->quantity + $quantity),
                'updated_at' => now(),
            ]);
        } else {
            DB::table('cart_items')->insert([
                'buyer_id' => $user->id,
                'product_id' => $productId,
                'quantity' => $quantity,
                'created_at' => now(),
                'updated_at' => now(),
            ]);
        }

        return response()->json(array_merge([
            'message' => 'Cart updated.',
        ], $this->cartPayload((int) $user->id)));
    }

    public function removeCartItem(Request $request, int $productId)
    {
        $user = $request->user();

        if ($user->role !== 'buyer') {
            return $this->forbidden('Only buyers can remove cart items.');
        }

        DB::table('cart_items')
            ->where('buyer_id', $user->id)
            ->where('product_id', $productId)
            ->delete();

        return response()->json(array_merge([
            'message' => 'Cart item removed.',
        ], $this->cartPayload((int) $user->id)));
    }

    public function checkout(Request $request)
    {
        $user = $request->user();

        if ($user->role !== 'buyer') {
            return $this->forbidden('Only buyers can create orders.');
        }

        $rows = DB::table('cart_items')
            ->join('products', 'products.id', '=', 'cart_items.product_id')
            ->where('cart_items.buyer_id', $user->id)
            ->where('products.status', 'published')
            ->get([
                'cart_items.product_id',
                'cart_items.quantity',
                'products.vendor_id',
                'products.name',
                'products.unit',
                'products.price',
            ]);

        if ($rows->isEmpty()) {
            return response()->json([
                'message' => 'Cart is empty.',
                'code' => 'CART_EMPTY',
            ], 409);
        }

        $orders = DB::transaction(function () use ($rows, $user): array {
            $createdOrders = [];

            foreach ($rows->groupBy('vendor_id') as $vendorId => $vendorRows) {
                $total = $vendorRows->reduce(function ($sum, $row) {
                    return $sum + ((float) $row->price * (int) $row->quantity);
                }, 0.0);

                $orderId = DB::table('orders')->insertGetId([
                    'buyer_id' => $user->id,
                    'vendor_id' => (int) $vendorId,
                    'total' => round($total, 2),
                    'status' => 'new',
                    'created_at' => now(),
                    'updated_at' => now(),
                ]);

                foreach ($vendorRows as $row) {
                    DB::table('order_items')->insert([
                        'order_id' => $orderId,
                        'product_id' => $row->product_id,
                        'product_name' => $row->name,
                        'unit' => $row->unit,
                        'unit_price' => $row->price,
                        'quantity' => $row->quantity,
                        'created_at' => now(),
                        'updated_at' => now(),
                    ]);
                }

                DB::table('order_messages')->insert([
                    'order_id' => $orderId,
                    'author_id' => $user->id,
                    'text' => 'Заказ создан. Ожидаю подтверждения продавца.',
                    'created_at' => now(),
                    'updated_at' => now(),
                ]);

                $createdOrders[] = $this->orderPayload(DB::table('orders')->where('id', $orderId)->first());
            }

            DB::table('cart_items')->where('buyer_id', $user->id)->delete();

            return $createdOrders;
        });

        return response()->json([
            'message' => 'Order created.',
            'orders' => $orders,
        ], 201);
    }

    public function orders(Request $request)
    {
        $user = $request->user();
        $query = DB::table('orders')->orderByDesc('created_at');

        if ($user->role === 'buyer') {
            $query->where('buyer_id', $user->id);
        } elseif ($user->role === 'vendor') {
            $query->where('vendor_id', $user->id);
        } else {
            return $this->forbidden('Unsupported role.');
        }

        return response()->json([
            'orders' => $query->get()->map(function ($order): array {
                return $this->orderPayload($order);
            })->values(),
        ]);
    }

    public function updateOrderStatus(Request $request, int $orderId)
    {
        $user = $request->user();
        $validator = Validator::make($request->all(), [
            'status' => ['required', Rule::in(['confirmed', 'completed'])],
        ]);

        if ($validator->fails()) {
            return $this->validationError($validator->errors()->toArray());
        }

        $order = DB::table('orders')->where('id', $orderId)->first();

        if (!$order || $user->role !== 'vendor' || (int) $order->vendor_id !== (int) $user->id) {
            return $this->notFound();
        }

        $nextStatus = (string) $request->input('status');
        $allowed = ($order->status === 'new' && $nextStatus === 'confirmed')
            || ($order->status === 'confirmed' && $nextStatus === 'completed');

        if (!$allowed) {
            return response()->json([
                'message' => 'Order status transition is invalid.',
                'code' => 'ORDER_STATUS_INVALID',
            ], 409);
        }

        DB::table('orders')->where('id', $orderId)->update([
            'status' => $nextStatus,
            'updated_at' => now(),
        ]);

        return response()->json([
            'message' => 'Order status updated.',
            'order' => $this->orderPayload(DB::table('orders')->where('id', $orderId)->first()),
        ]);
    }

    public function messages(Request $request, int $orderId)
    {
        $order = $this->participantOrder($request, $orderId);

        if (!$order) {
            return $this->notFound();
        }

        $messages = DB::table('order_messages')
            ->join('users', 'users.id', '=', 'order_messages.author_id')
            ->where('order_messages.order_id', $orderId)
            ->orderBy('order_messages.created_at')
            ->get([
                'order_messages.id',
                'order_messages.order_id',
                'order_messages.author_id',
                'order_messages.text',
                'order_messages.created_at',
                'users.name as author_name',
            ])
            ->map(function ($message): array {
                return [
                    'id' => (int) $message->id,
                    'orderId' => (int) $message->order_id,
                    'authorId' => (int) $message->author_id,
                    'authorName' => (string) $message->author_name,
                    'text' => (string) $message->text,
                    'createdAt' => (string) $message->created_at,
                ];
            })->values();

        return response()->json([
            'order' => $this->orderPayload($order),
            'messages' => $messages,
        ]);
    }

    public function sendMessage(Request $request, int $orderId)
    {
        $order = $this->participantOrder($request, $orderId);

        if (!$order) {
            return $this->notFound();
        }

        $validator = Validator::make($request->all(), [
            'text' => ['required', 'string', 'min:1', 'max:2000'],
        ]);

        if ($validator->fails()) {
            return $this->validationError($validator->errors()->toArray());
        }

        $messageId = DB::table('order_messages')->insertGetId([
            'order_id' => $orderId,
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

    private function findProduct(int $productId): array
    {
        $product = DB::table('products')
            ->leftJoin('users', 'users.id', '=', 'products.vendor_id')
            ->where('products.id', $productId)
            ->select('products.*', 'users.name as vendor_name')
            ->first();

        return $this->productPayload($product);
    }

    private function productPayload($product): array
    {
        return [
            'id' => (int) $product->id,
            'vendorId' => (int) $product->vendor_id,
            'vendorName' => (string) ($product->vendor_name ?? ''),
            'name' => (string) $product->name,
            'category' => (string) $product->category,
            'price' => (float) $product->price,
            'unit' => (string) $product->unit,
            'emoji' => (string) $product->emoji,
            'status' => (string) $product->status,
            'description' => (string) $product->description,
            'createdAt' => (string) $product->created_at,
        ];
    }

    private function cartPayload(int $buyerId): array
    {
        $items = DB::table('cart_items')
            ->join('products', 'products.id', '=', 'cart_items.product_id')
            ->leftJoin('users', 'users.id', '=', 'products.vendor_id')
            ->where('cart_items.buyer_id', $buyerId)
            ->orderBy('cart_items.created_at')
            ->get([
                'cart_items.product_id',
                'cart_items.quantity',
                'products.id',
                'products.vendor_id',
                'products.name',
                'products.category',
                'products.price',
                'products.unit',
                'products.emoji',
                'products.status',
                'products.description',
                'products.created_at',
                'users.name as vendor_name',
            ])
            ->map(function ($row): array {
                return [
                    'productId' => (int) $row->product_id,
                    'quantity' => (int) $row->quantity,
                    'lineTotal' => round((float) $row->price * (int) $row->quantity, 2),
                    'product' => $this->productPayload($row),
                ];
            })->values();

        return [
            'items' => $items,
            'total' => round($items->sum('lineTotal'), 2),
        ];
    }

    private function orderPayload($order): array
    {
        $buyer = DB::table('users')->where('id', $order->buyer_id)->first();
        $vendor = DB::table('users')->where('id', $order->vendor_id)->first();
        $items = DB::table('order_items')
            ->where('order_id', $order->id)
            ->orderBy('id')
            ->get()
            ->map(function ($item): array {
                return [
                    'id' => (int) $item->id,
                    'productId' => $item->product_id === null ? null : (int) $item->product_id,
                    'name' => (string) $item->product_name,
                    'unit' => (string) $item->unit,
                    'unitPrice' => (float) $item->unit_price,
                    'quantity' => (int) $item->quantity,
                    'lineTotal' => round((float) $item->unit_price * (int) $item->quantity, 2),
                ];
            })->values();

        return [
            'id' => (int) $order->id,
            'buyerId' => (int) $order->buyer_id,
            'buyerName' => $buyer ? (string) $buyer->name : '',
            'vendorId' => (int) $order->vendor_id,
            'vendorName' => $vendor ? (string) $vendor->name : '',
            'total' => (float) $order->total,
            'status' => (string) $order->status,
            'createdAt' => (string) $order->created_at,
            'items' => $items,
        ];
    }

    private function participantOrder(Request $request, int $orderId)
    {
        $order = DB::table('orders')->where('id', $orderId)->first();

        if (!$order) {
            return null;
        }

        $user = $request->user();
        $participant = ($user->role === 'buyer' && (int) $order->buyer_id === (int) $user->id)
            || ($user->role === 'vendor' && (int) $order->vendor_id === (int) $user->id);

        return $participant ? $order : null;
    }

    private function forbidden(string $message)
    {
        return response()->json([
            'message' => $message,
            'code' => 'FORBIDDEN',
        ], 403);
    }

    private function notFound()
    {
        return response()->json([
            'message' => 'Resource not found.',
            'code' => 'NOT_FOUND',
        ], 404);
    }

    private function validationError(array $errors)
    {
        return response()->json([
            'message' => 'The submitted data is invalid.',
            'code' => 'VALIDATION_ERROR',
            'errors' => $errors,
        ], 422);
    }
}
