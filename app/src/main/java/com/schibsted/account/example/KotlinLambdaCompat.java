package com.schibsted.account.example;

import androidx.core.util.Consumer;

import kotlin.Unit;
import kotlin.jvm.functions.Function1;

class KotlinLambdaCompat<T> implements Function1<T, Unit> {
    private Consumer<T> callback;

    private KotlinLambdaCompat(Consumer<T> callback) {
        this.callback = callback;
    }

    public static <T> KotlinLambdaCompat<T> wrap(Consumer<T> callback) {
        return new KotlinLambdaCompat<>(callback);
    }

    @Override
    public Unit invoke(T value) {
        callback.accept(value);
        return null;
    }
}
