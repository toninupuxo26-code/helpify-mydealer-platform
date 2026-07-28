<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;

class CreateHelpifyWorkflowTables extends Migration
{
    public function up(): void
    {
        Schema::create('tasks', function (Blueprint $table): void {
            $table->bigIncrements('id');
            $table->unsignedBigInteger('customer_id');
            $table->string('title', 160);
            $table->string('category', 80);
            $table->string('address', 190);
            $table->text('description');
            $table->decimal('budget', 10, 2);
            $table->string('status', 24)->default('open');
            $table->unsignedBigInteger('selected_offer_id')->nullable();
            $table->timestamps();

            $table->foreign('customer_id')->references('id')->on('users')->onDelete('cascade');
            $table->index(['status', 'created_at']);
        });

        Schema::create('task_offers', function (Blueprint $table): void {
            $table->bigIncrements('id');
            $table->unsignedBigInteger('task_id');
            $table->unsignedBigInteger('contractor_id');
            $table->decimal('price', 10, 2);
            $table->decimal('rating', 3, 2)->default(4.80);
            $table->string('distance', 40)->default('2,0 км');
            $table->string('status', 24)->default('pending');
            $table->timestamps();

            $table->foreign('task_id')->references('id')->on('tasks')->onDelete('cascade');
            $table->foreign('contractor_id')->references('id')->on('users')->onDelete('cascade');
            $table->unique(['task_id', 'contractor_id']);
        });

        Schema::create('task_messages', function (Blueprint $table): void {
            $table->bigIncrements('id');
            $table->unsignedBigInteger('task_id');
            $table->unsignedBigInteger('author_id');
            $table->text('text');
            $table->timestamps();

            $table->foreign('task_id')->references('id')->on('tasks')->onDelete('cascade');
            $table->foreign('author_id')->references('id')->on('users')->onDelete('cascade');
            $table->index(['task_id', 'created_at']);
        });

        $customer = DB::table('users')->where('email', 'customer@example.test')->first();
        $contractor = DB::table('users')->where('email', 'contractor@example.test')->first();

        if ($customer && $contractor) {
            $now = now();
            $taskId = DB::table('tasks')->insertGetId([
                'customer_id' => $customer->id,
                'title' => 'Установить смеситель',
                'category' => 'Сантехник',
                'address' => 'Рига, центр',
                'description' => 'Нужно заменить старый смеситель на кухне.',
                'budget' => 45.00,
                'status' => 'open',
                'selected_offer_id' => null,
                'created_at' => $now,
                'updated_at' => $now,
            ]);

            DB::table('tasks')->insert([
                'customer_id' => $customer->id,
                'title' => 'Повесить потолочный светильник',
                'category' => 'Электрик',
                'address' => 'Рига, Агенскалнс',
                'description' => 'Есть светильник и крепёж.',
                'budget' => 35.00,
                'status' => 'open',
                'selected_offer_id' => null,
                'created_at' => $now,
                'updated_at' => $now,
            ]);

            DB::table('task_offers')->insert([
                'task_id' => $taskId,
                'contractor_id' => $contractor->id,
                'price' => 40.00,
                'rating' => 4.90,
                'distance' => '1,2 км',
                'status' => 'pending',
                'created_at' => $now,
                'updated_at' => $now,
            ]);

            DB::table('task_messages')->insert([
                'task_id' => $taskId,
                'author_id' => $contractor->id,
                'text' => 'Добрый день! Могу приехать сегодня после 18:00.',
                'created_at' => $now,
                'updated_at' => $now,
            ]);
        }
    }

    public function down(): void
    {
        Schema::dropIfExists('task_messages');
        Schema::dropIfExists('task_offers');
        Schema::dropIfExists('tasks');
    }
}
