package com.mike.spi.log;

import com.mike.spi.annotation.MikeSPI;

/**
 * @Author: MikeWang
 * @Date: 2019/12/19 4:28 PM
 * @Description:
 */
@MikeSPI("mikeLog")
public interface Log {
    void sayHello();
}
