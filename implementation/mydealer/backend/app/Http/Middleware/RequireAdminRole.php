<?php

namespace App\Http\Middleware;

use Closure;
use Illuminate\Http\Request;

class RequireAdminRole
{
    public function handle(Request $request, Closure $next)
    {
        $user = $request->user();

        if (!$user || $user->role !== 'admin') {
            return response()->json([
                'message' => 'Administrator access is required.',
                'code' => 'ADMIN_ACCESS_REQUIRED',
            ], 403);
        }

        return $next($request);
    }
}
