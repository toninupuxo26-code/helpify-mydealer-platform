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
        'api' => 'v1-auth-marketplace-admin',
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
    ->prefix('market')
    ->group(function (): void {
        Route::get('/products', [\App\Http\Controllers\Api\MarketController::class, 'products']);
        Route::post('/products', [\App\Http\Controllers\Api\MarketController::class, 'storeProduct']);
        Route::post('/products/{productId}/publish', [\App\Http\Controllers\Api\MarketController::class, 'publishProduct']);
        Route::get('/cart', [\App\Http\Controllers\Api\MarketController::class, 'cart']);
        Route::post('/cart/items', [\App\Http\Controllers\Api\MarketController::class, 'addCartItem']);
        Route::delete('/cart/items/{productId}', [\App\Http\Controllers\Api\MarketController::class, 'removeCartItem']);
        Route::post('/orders/checkout', [\App\Http\Controllers\Api\MarketController::class, 'checkout']);
        Route::get('/orders', [\App\Http\Controllers\Api\MarketController::class, 'orders']);
        Route::post('/orders/{orderId}/status', [\App\Http\Controllers\Api\MarketController::class, 'updateOrderStatus']);
        Route::get('/orders/{orderId}/messages', [\App\Http\Controllers\Api\MarketController::class, 'messages']);
        Route::post('/orders/{orderId}/messages', [\App\Http\Controllers\Api\MarketController::class, 'sendMessage']);
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
        Route::get('/products', [\App\Http\Controllers\Api\AdminController::class, 'products']);
        Route::post('/products/{productId}/status', [\App\Http\Controllers\Api\AdminController::class, 'updateProductStatus']);
        Route::get('/orders', [\App\Http\Controllers\Api\AdminController::class, 'orders']);
        Route::post('/orders/{orderId}/status', [\App\Http\Controllers\Api\AdminController::class, 'updateOrderStatus']);
    });
