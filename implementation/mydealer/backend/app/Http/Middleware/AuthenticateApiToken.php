<?php

namespace App\Http\Middleware;

use Closure;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;

class AuthenticateApiToken
{
    public function handle(Request $request, Closure $next)
    {
        $authorization = (string) $request->header('Authorization', '');

        if (!preg_match('/^Bearer\s+(.+)$/i', $authorization, $matches)) {
            return response()->json([
                'message' => 'Authentication token is required.',
                'code' => 'AUTH_TOKEN_REQUIRED',
            ], 401);
        }

        $plainToken = trim($matches[1]);

        if ($plainToken === '') {
            return response()->json([
                'message' => 'Authentication token is required.',
                'code' => 'AUTH_TOKEN_REQUIRED',
            ], 401);
        }

        $token = DB::table('api_tokens')
            ->where('token_hash', hash('sha256', $plainToken))
            ->where(function ($query): void {
                $query->whereNull('expires_at')
                    ->orWhere('expires_at', '>', now());
            })
            ->first();

        if (!$token) {
            return response()->json([
                'message' => 'Authentication token is invalid or expired.',
                'code' => 'AUTH_TOKEN_INVALID',
            ], 401);
        }

        $user = DB::table('users')
            ->where('id', $token->user_id)
            ->where('status', 'active')
            ->first();

        if (!$user) {
            return response()->json([
                'message' => 'User is unavailable.',
                'code' => 'AUTH_USER_UNAVAILABLE',
            ], 401);
        }

        DB::table('api_tokens')
            ->where('id', $token->id)
            ->update([
                'last_used_at' => now(),
                'updated_at' => now(),
            ]);

        $request->attributes->set('api_token_id', $token->id);
        $request->setUserResolver(function () use ($user) {
            return $user;
        });

        return $next($request);
    }
}
