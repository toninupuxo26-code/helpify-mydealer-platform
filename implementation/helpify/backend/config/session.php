<?php
return [
    'driver' => env('SESSION_DRIVER', 'array'),
    'lifetime' => 120,
    'expire_on_close' => false,
    'encrypt' => false,
    'files' => storage_path('framework/sessions'),
    'connection' => env('SESSION_CONNECTION'),
    'table' => 'sessions',
    'store' => env('SESSION_STORE'),
    'lottery' => [2, 100],
    'cookie' => env('SESSION_COOKIE', env('APP_PRODUCT', 'app').'_session'),
    'path' => '/',
    'domain' => env('SESSION_DOMAIN'),
    'secure' => true,
    'http_only' => true,
    'same_site' => 'lax',
];
