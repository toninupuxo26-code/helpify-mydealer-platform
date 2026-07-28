<?php

namespace App\Http\Controllers\Api;

use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Hash;
use Illuminate\Support\Facades\Validator;
use Illuminate\Validation\Rule;

class AuthController
{
    public function capabilities()
    {
        return response()->json([
            'status' => 'ok',
            'product' => config('demo.product'),
            'version' => '0.7.0',
            'authentication' => 'bearer-token',
            'roles' => config('demo.registration_roles', config('demo.roles')),
            'administrator_console' => true,
            'password_recovery' => app()->environment('demo', 'local', 'testing')
                ? 'test-code'
                : 'out-of-band',
        ]);
    }

    public function register(Request $request)
    {
        $validator = Validator::make($request->all(), [
            'name' => ['required', 'string', 'min:2', 'max:120'],
            'email' => ['required', 'email', 'max:190', 'unique:users,email'],
            'password' => ['required', 'string', 'min:6', 'max:72'],
            'role' => ['required', Rule::in(config('demo.registration_roles', config('demo.roles')))],
        ]);

        if ($validator->fails()) {
            return $this->validationError($validator->errors()->toArray());
        }

        $now = now();
        $userId = DB::table('users')->insertGetId([
            'name' => trim((string) $request->input('name')),
            'email' => strtolower(trim((string) $request->input('email'))),
            'role' => (string) $request->input('role'),
            'status' => 'active',
            'password' => Hash::make((string) $request->input('password')),
            'created_at' => $now,
            'updated_at' => $now,
        ]);

        $user = DB::table('users')->where('id', $userId)->first();
        $issued = $this->issueToken($userId, 'registration');

        return response()->json([
            'message' => 'Registration completed.',
            'user' => $this->userPayload($user),
            'token' => $issued['token'],
            'token_type' => 'Bearer',
            'expires_at' => $issued['expires_at'],
        ], 201);
    }

    public function login(Request $request)
    {
        $validator = Validator::make($request->all(), [
            'email' => ['required', 'email', 'max:190'],
            'password' => ['required', 'string', 'max:72'],
        ]);

        if ($validator->fails()) {
            return $this->validationError($validator->errors()->toArray());
        }

        $email = strtolower(trim((string) $request->input('email')));
        $user = DB::table('users')->where('email', $email)->first();

        if (!$user || $user->status !== 'active' || !Hash::check((string) $request->input('password'), $user->password)) {
            return response()->json([
                'message' => 'Email or password is incorrect.',
                'code' => 'AUTH_CREDENTIALS_INVALID',
            ], 401);
        }

        $issued = $this->issueToken((int) $user->id, 'login');

        return response()->json([
            'message' => 'Login completed.',
            'user' => $this->userPayload($user),
            'token' => $issued['token'],
            'token_type' => 'Bearer',
            'expires_at' => $issued['expires_at'],
        ]);
    }

    public function me(Request $request)
    {
        return response()->json([
            'user' => $this->userPayload($request->user()),
        ]);
    }

    public function logout(Request $request)
    {
        $tokenId = (int) $request->attributes->get('api_token_id');

        if ($tokenId > 0) {
            DB::table('api_tokens')->where('id', $tokenId)->delete();
        }

        return response()->json([
            'message' => 'Logout completed.',
        ]);
    }

    public function forgotPassword(Request $request)
    {
        $validator = Validator::make($request->all(), [
            'email' => ['required', 'email', 'max:190'],
        ]);

        if ($validator->fails()) {
            return $this->validationError($validator->errors()->toArray());
        }

        $email = strtolower(trim((string) $request->input('email')));
        $user = DB::table('users')->where('email', $email)->first();
        $response = [
            'message' => 'If the account exists, password recovery instructions were created.',
            'expires_in_seconds' => (int) config('demo.password_reset_ttl_minutes', 15) * 60,
        ];

        if ($user) {
            $code = (string) random_int(100000, 999999);

            DB::table('password_reset_codes')
                ->where('email', $email)
                ->whereNull('used_at')
                ->update([
                    'used_at' => now(),
                    'updated_at' => now(),
                ]);

            DB::table('password_reset_codes')->insert([
                'email' => $email,
                'code_hash' => hash('sha256', $code),
                'expires_at' => now()->addMinutes((int) config('demo.password_reset_ttl_minutes', 15)),
                'used_at' => null,
                'created_at' => now(),
                'updated_at' => now(),
            ]);

            if (app()->environment('demo', 'local', 'testing')) {
                $response['demo_reset_code'] = $code;
            }
        }

        return response()->json($response);
    }

    public function resetPassword(Request $request)
    {
        $validator = Validator::make($request->all(), [
            'email' => ['required', 'email', 'max:190'],
            'code' => ['required', 'digits:6'],
            'password' => ['required', 'string', 'min:6', 'max:72'],
        ]);

        if ($validator->fails()) {
            return $this->validationError($validator->errors()->toArray());
        }

        $email = strtolower(trim((string) $request->input('email')));
        $codeHash = hash('sha256', (string) $request->input('code'));

        $reset = DB::table('password_reset_codes')
            ->where('email', $email)
            ->where('code_hash', $codeHash)
            ->whereNull('used_at')
            ->where('expires_at', '>', now())
            ->orderByDesc('id')
            ->first();

        if (!$reset) {
            return response()->json([
                'message' => 'Recovery code is invalid or expired.',
                'code' => 'PASSWORD_RESET_CODE_INVALID',
            ], 422);
        }

        $user = DB::table('users')->where('email', $email)->first();

        if (!$user) {
            return response()->json([
                'message' => 'Recovery code is invalid or expired.',
                'code' => 'PASSWORD_RESET_CODE_INVALID',
            ], 422);
        }

        DB::transaction(function () use ($reset, $user, $request): void {
            DB::table('password_reset_codes')
                ->where('id', $reset->id)
                ->update([
                    'used_at' => now(),
                    'updated_at' => now(),
                ]);

            DB::table('users')
                ->where('id', $user->id)
                ->update([
                    'password' => Hash::make((string) $request->input('password')),
                    'updated_at' => now(),
                ]);

            DB::table('api_tokens')->where('user_id', $user->id)->delete();
        });

        return response()->json([
            'message' => 'Password was updated. Existing sessions were closed.',
        ]);
    }

    private function issueToken(int $userId, string $name): array
    {
        $plainToken = bin2hex(random_bytes(32));
        $expiresAt = now()->addDays((int) config('demo.token_ttl_days', 30));

        DB::table('api_tokens')->insert([
            'user_id' => $userId,
            'name' => $name,
            'token_hash' => hash('sha256', $plainToken),
            'last_used_at' => null,
            'expires_at' => $expiresAt,
            'created_at' => now(),
            'updated_at' => now(),
        ]);

        return [
            'token' => $plainToken,
            'expires_at' => $expiresAt->toIso8601String(),
        ];
    }

    private function userPayload($user): array
    {
        return [
            'id' => (int) $user->id,
            'name' => (string) $user->name,
            'email' => (string) $user->email,
            'role' => (string) $user->role,
            'status' => (string) $user->status,
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
}
