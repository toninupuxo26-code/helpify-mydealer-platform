<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;

class CreateMydealerWorkflowTables extends Migration
{
    public function up(): void
    {
        Schema::create('products', function (Blueprint $table): void {
            $table->bigIncrements('id');
            $table->unsignedBigInteger('vendor_id');
            $table->string('name', 160);
            $table->string('category', 100);
            $table->decimal('price', 12, 2);
            $table->string('unit', 60);
            $table->string('emoji', 16)->default('🌿');
            $table->string('status', 24)->default('moderation');
            $table->text('description');
            $table->timestamps();

            $table->foreign('vendor_id')->references('id')->on('users')->onDelete('cascade');
            $table->index(['status', 'category']);
        });

        Schema::create('cart_items', function (Blueprint $table): void {
            $table->bigIncrements('id');
            $table->unsignedBigInteger('buyer_id');
            $table->unsignedBigInteger('product_id');
            $table->unsignedInteger('quantity')->default(1);
            $table->timestamps();

            $table->foreign('buyer_id')->references('id')->on('users')->onDelete('cascade');
            $table->foreign('product_id')->references('id')->on('products')->onDelete('cascade');
            $table->unique(['buyer_id', 'product_id']);
        });

        Schema::create('orders', function (Blueprint $table): void {
            $table->bigIncrements('id');
            $table->unsignedBigInteger('buyer_id');
            $table->unsignedBigInteger('vendor_id');
            $table->decimal('total', 12, 2);
            $table->string('status', 24)->default('new');
            $table->timestamps();

            $table->foreign('buyer_id')->references('id')->on('users')->onDelete('cascade');
            $table->foreign('vendor_id')->references('id')->on('users')->onDelete('cascade');
            $table->index(['buyer_id', 'status']);
            $table->index(['vendor_id', 'status']);
        });

        Schema::create('order_items', function (Blueprint $table): void {
            $table->bigIncrements('id');
            $table->unsignedBigInteger('order_id');
            $table->unsignedBigInteger('product_id')->nullable();
            $table->string('product_name', 160);
            $table->string('unit', 60);
            $table->decimal('unit_price', 12, 2);
            $table->unsignedInteger('quantity');
            $table->timestamps();

            $table->foreign('order_id')->references('id')->on('orders')->onDelete('cascade');
            $table->foreign('product_id')->references('id')->on('products')->onDelete('set null');
        });

        Schema::create('order_messages', function (Blueprint $table): void {
            $table->bigIncrements('id');
            $table->unsignedBigInteger('order_id');
            $table->unsignedBigInteger('author_id');
            $table->text('text');
            $table->timestamps();

            $table->foreign('order_id')->references('id')->on('orders')->onDelete('cascade');
            $table->foreign('author_id')->references('id')->on('users')->onDelete('cascade');
        });

        $buyer = DB::table('users')->where('role', 'buyer')->first();
        $vendor = DB::table('users')->where('role', 'vendor')->first();

        if ($buyer && $vendor) {
            $now = now();
            $products = [
                ['name' => 'Фермерский сыр', 'category' => 'Молочные продукты', 'price' => 18.00, 'unit' => '500 г', 'emoji' => '🧀', 'description' => 'Выдержанный сыр из цельного молока.', 'status' => 'published'],
                ['name' => 'Трюфельное масло', 'category' => 'Деликатесы', 'price' => 29.00, 'unit' => '250 мл', 'emoji' => '🫒', 'description' => 'Масло холодного отжима с ароматом трюфеля.', 'status' => 'published'],
                ['name' => 'Хлеб на закваске', 'category' => 'Выпечка', 'price' => 7.00, 'unit' => '1 шт.', 'emoji' => '🥖', 'description' => 'Ручная выпечка без улучшителей.', 'status' => 'moderation'],
            ];
            $productIds = [];

            foreach ($products as $product) {
                $productIds[] = DB::table('products')->insertGetId(array_merge($product, [
                    'vendor_id' => $vendor->id,
                    'created_at' => $now,
                    'updated_at' => $now,
                ]));
            }

            $orderId = DB::table('orders')->insertGetId([
                'buyer_id' => $buyer->id,
                'vendor_id' => $vendor->id,
                'total' => 36.00,
                'status' => 'confirmed',
                'created_at' => $now,
                'updated_at' => $now,
            ]);

            DB::table('order_items')->insert([
                'order_id' => $orderId,
                'product_id' => $productIds[0],
                'product_name' => 'Фермерский сыр',
                'unit' => '500 г',
                'unit_price' => 18.00,
                'quantity' => 2,
                'created_at' => $now,
                'updated_at' => $now,
            ]);

            DB::table('order_messages')->insert([
                ['order_id' => $orderId, 'author_id' => $vendor->id, 'text' => 'Добрый день! Заказ будет готов к отправке завтра.', 'created_at' => $now, 'updated_at' => $now],
                ['order_id' => $orderId, 'author_id' => $buyer->id, 'text' => 'Спасибо, ожидаю подтверждение времени.', 'created_at' => $now, 'updated_at' => $now],
            ]);
        }
    }

    public function down(): void
    {
        Schema::dropIfExists('order_messages');
        Schema::dropIfExists('order_items');
        Schema::dropIfExists('orders');
        Schema::dropIfExists('cart_items');
        Schema::dropIfExists('products');
    }
}
