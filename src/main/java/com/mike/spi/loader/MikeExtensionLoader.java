package com.mike.spi.loader;

import com.mike.spi.annotation.MikeSPI;
import com.mike.spi.support.Holder;
import com.mike.spi.util.StringUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;

/**
 * @Author: MikeWang
 * @Date: 2019/12/19 11:21 AM
 * @Description:
 */
public class MikeExtensionLoader<T> {
    /**
     * 定义SPI 文件扫描的扫描路径，dubbo源码中设置了多个，我们这里只设置一个路径就够了
     */
    private static final String MIKE_SERVICES_DIRECTORY = "META-INF/mike/";

    /**
     * 分割SPI 上默认扩展点字符串用的
     */
    private static final Pattern NAME_SEPARATOR =Pattern.compile("\\s*[,]+\\s*");

    /**
     * 扩展点加载器的缓存
     */
    private static final ConcurrentMap<Class<?>, MikeExtensionLoader<?>> EXTENSION_LOADERS = new ConcurrentHashMap<>();

    private static final ConcurrentMap<Class<?>,Object> EXTENSION_INSTANCES = new ConcurrentHashMap<>();

    /**
     * 接口的class
     */
    private final Class<?> type;

    /**
     * 接口SPI 默认的实现名（就是把接口上填上默认值）
     */
    private String cachedDefaultName;

    /**
     * 异常记录
     */
    private Map<String,IllegalStateException> exceptions = new ConcurrentHashMap<>();

    //当前线程的缓存
    private final Holder<Map<String,Class<?>>> cachedClasses = new Holder<>();
    private final ConcurrentMap<String,Holder<Object>> cachedInstances = new ConcurrentHashMap<>();


    private static <T> boolean withExtensionAnnotation(Class<T> type){
        return type.isAnnotationPresent(MikeSPI.class);
    }

    public MikeExtensionLoader(Class<?> type) {
        this.type = type;
    }

    public static <T> MikeExtensionLoader<T> getExtensionLoader(Class<T> type){
        if (type == null){//1.扩展点类型非空判断
            throw new IllegalArgumentException("Extension type == null");
        }
        if (!type.isInterface()){//2.扩展点类型只能是接口
            throw new IllegalArgumentException("Extension type(" + type + ") is not interface!");
        }
        if (!withExtensionAnnotation(type)){//3.需要添加spi 注解，否则抛出异常。
            throw new IllegalArgumentException("Extension type(" + type +
                    ") is not extension, because WITHOUT @" + MikeSPI.class.getSimpleName() + " Annotation!");
        }
        MikeExtensionLoader<T> loader = (MikeExtensionLoader<T>) EXTENSION_LOADERS.get(type);
        if (loader == null){
            EXTENSION_LOADERS.putIfAbsent(type,new MikeExtensionLoader<T>(type));
            loader = (MikeExtensionLoader<T>) EXTENSION_LOADERS.get(type);
        }
        return loader;
    }

    public T getExtension(String name){
        if (name == null || name.isEmpty()){
            throw new IllegalArgumentException("Extension name == null");
        }
        if ("true".equals(name)) {
            return getDefaultExtension();
        }
        // Holder 用于持有目标对象  先从缓存中去取
        final Holder<Object> holder = getOrCreateHolder(name);
        Object instance = holder.get();
        // 双重检查
        if (instance == null){
            synchronized (holder){
                instance = holder.get();
                if (instance == null){
                    //创建实例
                    instance = createExtension(name);
                    holder.set(instance);
                }
            }
        }else {
            System.out.println(name+"已经实例化");
        }
        return (T) instance;
    }

    private T createExtension(String name){
        Class<?> clazz = getExtensionClasses().get(name);
        if (clazz == null){
            throw findException(name);
        }
        try {
            T instance = (T) EXTENSION_INSTANCES.get(clazz);
            if (instance == null){
                EXTENSION_INSTANCES.putIfAbsent(clazz,(T) clazz.newInstance());
                instance = (T) EXTENSION_INSTANCES.get(clazz);
            }
            return instance;
            //========================================部分源码逻辑未实现
        }catch (Throwable t){
            throw new IllegalStateException("Extension instance(name: " + name + ", class: " +
                    type + ")  could not be instantiated: " + t.getMessage(), t);
        }
    }

    private Holder<Object> getOrCreateHolder(String name) {
        Holder<Object> holder = cachedInstances.get(name);
        if (holder == null) {
            //如果缓存中没有的话在创建一个然后放进去，但是此时并没有实际内容，只有一个空的容器Holder
            cachedInstances.putIfAbsent(name, new Holder<>());
            holder = cachedInstances.get(name);
        }
        return holder;
    }

    //获取默认扩展点
    public T getDefaultExtension(){
        getExtensionClasses();
        if (StringUtils.isBlank(cachedDefaultName) || "true".equals(cachedDefaultName)) {
            return null;
        }
        return getExtension(cachedDefaultName);
    }

    // 获取扩展点class,并缓存
    private Map<String,Class<?>> getExtensionClasses(){
        Map<String,Class<?>> classes = cachedClasses.get();
        if (classes == null){
            synchronized (cachedClasses){
                classes = cachedClasses.get();
                if (classes ==null){
                    classes = loadExtensionClasses();
                    cachedClasses.set(classes);
                }
            }
        }
        return classes;
    }

    private Map<String,Class<?>> loadExtensionClasses(){
        //设置默认扩展名称
        cacheDefaultExtensionName();
        Map<String,Class<?>> extensionClasses = new HashMap<>();
        loadDirectory(extensionClasses, MIKE_SERVICES_DIRECTORY, type.getName());
        //loadFile(extensionClasses, MIKE_SERVICES_DIRECTORY);
        return extensionClasses;
    }



    private void cacheDefaultExtensionName(){
        final MikeSPI defaultAnnotation = type.getAnnotation(MikeSPI.class);
        if (defaultAnnotation == null){
            return ;
        }
        String value = defaultAnnotation.value();
        if ((value = value.trim()).length() > 0) {
            // 对SPI 注解内容进行切分
            //一个@SPI注解的值只能有一个
            String[] names = NAME_SEPARATOR.split(value);
            // 检测 SPI 注解内容是否合法，不合法则抛出异常
            if (names.length > 1) {
                throw new IllegalStateException("More than 1 default extension name on extension " + type.getName()
                        + ": " + Arrays.toString(names));
            }
            // 设置默认名称，参考getDefaultExtension 方法
            //cachedDefaultName表示该扩展点对应的默认适配类的key
            //逻辑运行到这里就意味着该扩展点有定义的适配类，不需要Dubbo框架自己生成适配类
            if (names.length == 1) {
                cachedDefaultName = names[0];
            }
        }
    }

    //加载相关路径下的类文件
    private void loadDirectory(Map<String, Class<?>> extensionClasses, String dir, String type) {
        loadDirectory(extensionClasses, dir, type, false);
    }

    private void loadDirectory(Map<String, Class<?>> extensionClasses, String dir, String type, boolean extensionLoaderClassLoaderFirst) {
        // fileName = 文件夹路径 + type 全限定名
        //加载这些文件你的classloader要和加载当前类的classloader一致,这个类似与Java默认的类加载器和类的加载关系
        String fileName = dir + type;
        try {
            Enumeration<URL> urls = null;
            ClassLoader classLoader = findClassLoader();

            // try to load from ExtensionLoader's ClassLoader first
            if (extensionLoaderClassLoaderFirst) {
                ClassLoader extensionLoaderClassLoader = MikeExtensionLoader.class.getClassLoader();
                if (ClassLoader.getSystemClassLoader() != extensionLoaderClassLoader) {
                    urls = extensionLoaderClassLoader.getResources(fileName);
                }
            }

            if(urls == null || !urls.hasMoreElements()) {
                if (classLoader != null) {
                    //加载资源
                    //该步骤就加载所有的classpath下面的同名文件（包含你的项目本地classpath和依赖jar包）
                    urls = classLoader.getResources(fileName);
                } else {
                    urls = ClassLoader.getSystemResources(fileName);
                }
            }

            if (urls != null) {
                //一般情况下每个包内只会对与每个扩展点放置一个类信息描述文件
                while (urls.hasMoreElements()) {
                    java.net.URL resourceURL = urls.nextElement();
                    loadResource(extensionClasses, classLoader, resourceURL);
                }
            }
        } catch (Throwable t) {
//            logger.error("Exception occurred when loading extension class (interface: " +
//                    type + ", description file: " + fileName + ").", t);
        }
    }

    private void loadResource(Map<String, Class<?>> extensionClasses, ClassLoader classLoader, java.net.URL resourceURL){
        try {
            try(BufferedReader reader = new BufferedReader(new InputStreamReader(resourceURL.openStream(),StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) !=null){
                    final int ci = line.indexOf('#');
                    if (ci>=0){
                        line = line.substring(0,ci);
                    }
                    line = line.trim();
                    if (line.length() >0){
                        try {
                            String name = null;
                            // 以等于号 = 为界，截取键与值
                            int i = line.indexOf('=');
                            if (i > 0) {
                                name = line.substring(0, i).trim();//SPI扩展文件中的key
                                line = line.substring(i + 1).trim();//SPI扩展文件中配置的value  ExtensionLoader是根据key和value同时加载的
                            }
                            if (line.length()>0){
                                //加载类，并通过loadClass 方法对类进行缓存
                                loadClass(extensionClasses, resourceURL, Class.forName(line, true, classLoader), name);
                            }
                        }catch (Throwable t){
                            IllegalStateException e = new IllegalStateException("Failed to load extension class (interface: " + type + ", class line: " + line + ") in " + resourceURL + ", cause: " + t.getMessage(), t);
                            exceptions.put(line,e);
                        }
                    }
                }
            }
        }catch (Exception e){

        }
    }

    private void loadClass(Map<String, Class<?>> extensionClasses, java.net.URL resourceURL, Class<?> clazz, String name) throws NoSuchMethodException {
        if (!type.isAssignableFrom(clazz)) {
            throw new IllegalStateException("Error when load extension class(interface: " +
                    type + ", class line: " + clazz.getName() + "), class "
                    + clazz.getName() + "is not subtype of interface.");
        }
        // 存储名称到 Class 的映射关系
        extensionClasses.put(name, clazz);//加入缓存
        //=============================================部分源码逻辑未实现======
    }

    private static ClassLoader findClassLoader(){
        return MikeExtensionLoader.class.getClassLoader();
    }

    private IllegalStateException findException(String name) {
        for (Map.Entry<String, IllegalStateException> entry : exceptions.entrySet()) {
            if (entry.getKey().toLowerCase().contains(name.toLowerCase())) {
                return entry.getValue();
            }
        }
        StringBuilder buf = new StringBuilder("No such extension " + type.getName() + " by name " + name);


        int i = 1;
        for (Map.Entry<String, IllegalStateException> entry : exceptions.entrySet()) {
            if (i == 1) {
                buf.append(", possible causes: ");
            }

            buf.append("\r\n(");
            buf.append(i++);
            buf.append(") ");
            buf.append(entry.getKey());
            buf.append(":\r\n");
            buf.append(entry.getValue().toString());
        }
        return new IllegalStateException(buf.toString());
    }



}
