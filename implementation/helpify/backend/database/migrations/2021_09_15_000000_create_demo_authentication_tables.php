<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;

class CreateDemoAuthenticationTables extends Migration
{
    public function up(): void
    {
        Schema::create('users', function (Blueprint $table): void {
            $table->bigIncrements('id');
            $table->string('name', 120);
            $table->string('email', 190)->unique();
            $table->string('role', 32);
            $table->string('status', 20)->default('active');
            $table->string('password');
            $table->timestamps();
        });

        Schema::create('api_tokens', function (Blueprint $table): void {
            $table->bigIncrements('id');
            $table->unsignedBigInteger('user_id');
            $table->string('name', 80)->default('api');
            $table->char('token_hash', 64)->unique();
            $table->timestamp('last_used_at')->nullable();
            $table->timestamp('expires_at')->nullable();
            $table->timestamps();

            $table->foreign('user_id')
                ->references('id')
                ->on('users')
                ->onDelete('cascade');
        });

        Schema::create('password_reset_codes', function (Blueprint $table): void {
            $table->bigIncrements('id');
            $table->string('email', 190)->index();
            $table->char('code_hash', 64);
            $table->timestamp('expires_at');
            $table->timestamp('used_at')->nullable();
            $table->timestamps();
        });

        $now = now();
        $product = (string) env('APP_PRODUCT', 'unknown');
        $users = $product === 'helpify'
            ? [
                ['name' => 'Helpify Customer', 'email' => 'customer@example.test', 'role' => 'customer'],
                ['name' => 'Helpify Contractor', 'email' => 'contractor@example.test', 'role' => 'contractor'],
            ]
            : [
                ['name' => 'MyDealer Buyer', 'email' => 'buyer@example.test', 'role' => 'buyer'],
                ['name' => 'MyDealer Vendor', 'email' => 'vendor@example.test', 'role' => 'vendor'],
            ];

        foreach ($users as $user) {
            DB::table('users')->insert([
                'name' => $user['name'],
                'email' => $user['email'],
                'role' => $user['role'],
                'status' => 'active',
                'password' => password_hash('demo123', PASSWORD_BCRYPT),
                'created_at' => $now,
                'updated_at' => $now,
            ]);
        }
    }

    public function down(): void
    {
        Schema::dropIfExists('password_reset_codes');
        Schema::dropIfExists('api_tokens');
        Schema::dropIfExists('users');
    }
}
