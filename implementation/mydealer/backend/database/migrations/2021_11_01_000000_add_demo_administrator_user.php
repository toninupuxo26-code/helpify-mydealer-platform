<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Support\Facades\DB;

class AddDemoAdministratorUser extends Migration
{
    public function up(): void
    {
        $email = 'admin@example.test';

        if (!DB::table('users')->where('email', $email)->exists()) {
            DB::table('users')->insert([
                'name' => 'Demo Administrator',
                'email' => $email,
                'role' => 'admin',
                'status' => 'active',
                'password' => password_hash('demo123', PASSWORD_BCRYPT),
                'created_at' => now(),
                'updated_at' => now(),
            ]);
        }
    }

    public function down(): void
    {
        DB::table('users')->where('email', 'admin@example.test')->delete();
    }
}
