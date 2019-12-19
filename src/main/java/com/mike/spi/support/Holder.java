package com.mike.spi.support;

/**
 *  Helper Class for hold a value
 *
 * @Author: MikeWang
 * @Date: 2019/12/19 1:49 PM
 * @Description:
 */
public class Holder<T> {

    private volatile T value;

    public T get() {
        return value;
    }

    public void set(T value) {
        this.value = value;
    }
}
