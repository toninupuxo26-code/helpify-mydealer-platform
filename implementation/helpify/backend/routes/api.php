<?php

use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Redis;
use Illuminate\Support\Facades\Route;

Route::get('/health', function () {
    $database = 'ok';
    $redis = 'ok';

    try {
        DB::select('SELECT 1');
    } catch (Throwable $exception) {
        $database = 'error';
    }

    try {
        Redis::connection()->ping();
    } catch (Throwable $exception) {
        $redis = 'error';
    }

    $healthy = $database === 'ok' && $redis === 'ok';

    return response()->json([
        'status' => $healthy ? 'ok' : 'degraded',
        'product' => env('APP_PRODUCT', 'unknown'),
        'application' => config('app.name'),
        'application_version' => '0.8.0',
        'framework' => 'Laravel '.app()->version(),
        'php' => PHP_VERSION,
        'database' => $database,
        'redis' => $redis,
        'environment' => app()->environment(),
        'timestamp_utc' => gmdate('c'),
    ], $healthy ? 200 : 503);
});

Route::get('/version', function () {
    return response()->json([
        'product' => env('APP_PRODUCT', 'unknown'),
        'version' => '0.8.0',
        'api' => 'v1-auth-workflow-admin',
    ]);
});

Route::prefix('auth')->group(function (): void {
    Route::get('/capabilities', [\App\Http\Controllers\Api\AuthController::class, 'capabilities']);
    Route::post('/register', [\App\Http\Controllers\Api\AuthController::class, 'register']);
    Route::post('/login', [\App\Http\Controllers\Api\AuthController::class, 'login']);
    Route::post('/password/forgot', [\App\Http\Controllers\Api\AuthController::class, 'forgotPassword']);
    Route::post('/password/reset', [\App\Http\Controllers\Api\AuthController::class, 'resetPassword']);

    Route::middleware(\App\Http\Middleware\AuthenticateApiToken::class)->group(function (): void {
        Route::get('/me', [\App\Http\Controllers\Api\AuthController::class, 'me']);
        Route::post('/logout', [\App\Http\Controllers\Api\AuthController::class, 'logout']);
    });
});

Route::middleware(\App\Http\Middleware\AuthenticateApiToken::class)
    ->prefix('work')
    ->group(function (): void {
        Route::get('/tasks', [\App\Http\Controllers\Api\TaskController::class, 'index']);
        Route::post('/tasks', [\App\Http\Controllers\Api\TaskController::class, 'store']);
        Route::get('/tasks/{taskId}', [\App\Http\Controllers\Api\TaskController::class, 'show']);
        Route::post('/tasks/{taskId}/offers', [\App\Http\Controllers\Api\TaskController::class, 'createOffer']);
        Route::post('/tasks/{taskId}/offers/{offerId}/select', [\App\Http\Controllers\Api\TaskController::class, 'selectOffer']);
        Route::post('/tasks/{taskId}/status', [\App\Http\Controllers\Api\TaskController::class, 'updateStatus']);
        Route::get('/tasks/{taskId}/messages', [\App\Http\Controllers\Api\TaskController::class, 'messages']);
        Route::post('/tasks/{taskId}/messages', [\App\Http\Controllers\Api\TaskController::class, 'sendMessage']);
    });

Route::middleware([
        \App\Http\Middleware\AuthenticateApiToken::class,
        \App\Http\Middleware\RequireAdminRole::class,
    ])
    ->prefix('admin')
    ->group(function (): void {
        Route::get('/dashboard', [\App\Http\Controllers\Api\AdminController::class, 'dashboard']);
        Route::get('/users', [\App\Http\Controllers\Api\AdminController::class, 'users']);
        Route::post('/users/{userId}/status', [\App\Http\Controllers\Api\AdminController::class, 'updateUserStatus']);
        Route::get('/tasks', [\App\Http\Controllers\Api\AdminController::class, 'tasks']);
        Route::post('/tasks/{taskId}/status', [\App\Http\Controllers\Api\AdminController::class, 'updateTaskStatus']);
    });
