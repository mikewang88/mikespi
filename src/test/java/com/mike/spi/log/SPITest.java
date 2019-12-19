package com.mike.spi.log;

import com.mike.spi.loader.MikeExtensionLoader;
import org.junit.Test;

/**
 * @Author: MikeWang
 * @Date: 2019/12/19 4:36 PM
 * @Description:
 */
public class SPITest {

    @Test
    public void test() throws Exception{
        Log defaultExtension = MikeExtensionLoader.getExtensionLoader(Log.class).getDefaultExtension();
        defaultExtension.sayHello();
    }

}
