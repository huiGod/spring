# AnnotationConfigApplicationContext初始化

1. 调用父类GenericApplicationContext初始化出默认工厂实现DefaultListableBeanFactory
2. 初始化AnnotatedBeanDefinitionReader注解读取器
3. 初始化ClassPathBeanDefinitionScanner扫描器

代码示例：

```java
public static void main(String[] args) {

		AnnotationConfigApplicationContext annotationConfigApplicationContext = new AnnotationConfigApplicationContext(Appconfig.class);

		annotationConfigApplicationContext.refresh();

		Dao dao = (Dao)annotationConfigApplicationContext.getBean("dao");
		dao.query();
	}
```

类结构图：

![img](/Users/huigod/Documents/Spring/Untitled.assets/screenshot.png)

因为AnnotationConfigApplicationContext有父类，在执行构造方法时，会先执行父类构造方法：

```java
public class GenericApplicationContext extends AbstractApplicationContext implements BeanDefinitionRegistry {

	private final DefaultListableBeanFactory beanFactory;

	@Nullable
	private ResourceLoader resourceLoader;

	private boolean customClassLoader = false;

	private final AtomicBoolean refreshed = new AtomicBoolean();
  
  	public GenericApplicationContext() {
    //初始化一个默认的 spring 工厂类
		this.beanFactory = new DefaultListableBeanFactory();
	}
  ...
}
```

AnnotationConfigApplicationContext类定义

通过分析这个类我们知道注册一个bean到spring容器有两种办法

一、直接将注解Bean注册到容器中：（参考）public void register(Class<?>... annotatedClasses)

但是直接把一个注解的bean注册到容器当中也分为两种方法

1、在初始化容器时注册并且解析

2、也可以在容器创建之后手动调用注册方法向容器注册，然后通过手动刷新容器，使得容器对注册的注解Bean进行处理。

思考：为什么@profile要使用这类的第2种方法

二、通过扫描指定的包及其子包下的所有类

扫描其实同上，也是两种方法，初始化的时候扫描，和初始化之后再扫描

```java
public class AnnotationConfigApplicationContext extends GenericApplicationContext implements AnnotationConfigRegistry {
  
  /**
	 * 读取一个被加了注解的bean
	 */
	private final AnnotatedBeanDefinitionReader reader;

	/**
	 * 扫描所有加了注解的bean
	 */
	private final ClassPathBeanDefinitionScanner scanner;

  /**
   *  初始化读取器和扫描器
   *  如果调用无参构造方法，需要后续调用 register 方法区注册配置类，并调用 refresh 方法
   */
  public AnnotationConfigApplicationContext() {

    //创建一个读取注解的Bean定义读取器
    this.reader = new AnnotatedBeanDefinitionReader(this);


    //可以用来扫描包或者类，继而转换成BeanDefinition
    //spring 会在内部new的一个ClassPathBeanDefinitionScanner来完成扫描，而不是这里的scanner对象
    //这里的scanner仅仅是为了在外部通过AnnotationConfigApplicationContext对象调用 scan方法
    this.scanner = new ClassPathBeanDefinitionScanner(this);
  }

  public AnnotationConfigApplicationContext(Class<?>... annotatedClasses) {
    ///先调用无参的构造方法
    this();
    //再将传入的类通过读取器进行读取并注册
    register(annotatedClasses);
    //刷新工厂
    refresh();
  }
  ...
}
```

## 构建AnnotatedBeanDefinitionReader对象

```java
/**
 *  AnnotationConfigApplicationContext本身就实现了BeanDefinitionRegistry接口，所以传入 this 即可
 */
public class AnnotatedBeanDefinitionReader {

  //BeanDefinition的注册器
	private final BeanDefinitionRegistry registry;

	private BeanNameGenerator beanNameGenerator = new AnnotationBeanNameGenerator();

	private ScopeMetadataResolver scopeMetadataResolver = new AnnotationScopeMetadataResolver();

	private ConditionEvaluator conditionEvaluator;
  
  public AnnotatedBeanDefinitionReader(BeanDefinitionRegistry registry) {
		this(registry, getOrCreateEnvironment(registry));
	}

  public AnnotatedBeanDefinitionReader(BeanDefinitionRegistry registry, Environment environment) {
    //		Assert.notNull(registry, "BeanDefinitionRegistry must not be null");
    //		Assert.notNull(environment, "Environment must not be null");
    this.registry = registry;
    ////处理Conditional注解
    this.conditionEvaluator = new ConditionEvaluator(registry, environment, null);
    //注册注解相关的 post 处理器
    AnnotationConfigUtils.registerAnnotationConfigProcessors(this.registry);
  }
  ...
}
```

注册注解相关的 post 处理器，这里都是一些 spring 内置的RootBeanDefinition(也是一种BeanDefinition)，用来给 spring 工厂提供额外的能力

ConfigurationClassPostProcessor和AutowiredAnnotationBeanPostProcessor是比较重要的

```java
public static void registerAnnotationConfigProcessors(BeanDefinitionRegistry registry) {
  registerAnnotationConfigProcessors(registry, null);
}
```

```java
public static Set<BeanDefinitionHolder> registerAnnotationConfigProcessors(
			BeanDefinitionRegistry registry, @Nullable Object source) {
	//获取在父类中初始化的DefaultListableBeanFactory工厂类
  DefaultListableBeanFactory beanFactory = unwrapDefaultListableBeanFactory(registry);
  if (beanFactory != null) {
    if (!(beanFactory.getDependencyComparator() instanceof AnnotationAwareOrderComparator)) {
      //AnnotationAwareOrderComparator提供解析@Order注解和@Priority的功能
      beanFactory.setDependencyComparator(AnnotationAwareOrderComparator.INSTANCE);
    }
    if (!(beanFactory.getAutowireCandidateResolver() instanceof ContextAnnotationAutowireCandidateResolver)) {
      //ContextAnnotationAutowireCandidateResolver提供处理延迟加载的功能
      beanFactory.setAutowireCandidateResolver(new ContextAnnotationAutowireCandidateResolver());
    }
  }

  Set<BeanDefinitionHolder> beanDefs = new LinkedHashSet<>(8);
  //BeanDefinitio的注册，这里很重要，需要理解注册每个bean的类型
  //org.springframework.context.annotation.internalConfigurationAnnotationProcessor
  if (!registry.containsBeanDefinition(CONFIGURATION_ANNOTATION_PROCESSOR_BEAN_NAME)) {
    //需要注意的是ConfigurationClassPostProcessor的类型是BeanDefinitionRegistryPostProcessor
		//而BeanDefinitionRegistryPostProcessor也继承BeanFactoryPostProcessor这个接口
    RootBeanDefinition def = new RootBeanDefinition(ConfigurationClassPostProcessor.class);
    def.setSource(source);
    //注册到 spring 容器并封装成BeanDefinitionHolder返回
    beanDefs.add(registerPostProcessor(registry, def, CONFIGURATION_ANNOTATION_PROCESSOR_BEAN_NAME));
  }

  //org.springframework.context.annotation.internalAutowiredAnnotationProcessor
  if (!registry.containsBeanDefinition(AUTOWIRED_ANNOTATION_PROCESSOR_BEAN_NAME)) {
    //AutowiredAnnotationBeanPostProcessor 实现了 MergedBeanDefinitionPostProcessor
    //MergedBeanDefinitionPostProcessor 也继承了 BeanPostProcessor
    RootBeanDefinition def = new RootBeanDefinition(AutowiredAnnotationBeanPostProcessor.class);
    def.setSource(source);
    beanDefs.add(registerPostProcessor(registry, def, AUTOWIRED_ANNOTATION_PROCESSOR_BEAN_NAME));
  }

  if (!registry.containsBeanDefinition(REQUIRED_ANNOTATION_PROCESSOR_BEAN_NAME)) {
    RootBeanDefinition def = new RootBeanDefinition(RequiredAnnotationBeanPostProcessor.class);
    def.setSource(source);
    beanDefs.add(registerPostProcessor(registry, def, REQUIRED_ANNOTATION_PROCESSOR_BEAN_NAME));
  }

  // Check for JSR-250 support, and if present add the CommonAnnotationBeanPostProcessor.
  if (jsr250Present && !registry.containsBeanDefinition(COMMON_ANNOTATION_PROCESSOR_BEAN_NAME)) {
    RootBeanDefinition def = new RootBeanDefinition(CommonAnnotationBeanPostProcessor.class);
    def.setSource(source);
    beanDefs.add(registerPostProcessor(registry, def, COMMON_ANNOTATION_PROCESSOR_BEAN_NAME));
  }

  // Check for JPA support, and if present add the PersistenceAnnotationBeanPostProcessor.
  if (jpaPresent && !registry.containsBeanDefinition(PERSISTENCE_ANNOTATION_PROCESSOR_BEAN_NAME)) {
    RootBeanDefinition def = new RootBeanDefinition();
    try {
      def.setBeanClass(ClassUtils.forName(PERSISTENCE_ANNOTATION_PROCESSOR_CLASS_NAME,
                                          AnnotationConfigUtils.class.getClassLoader()));
    }
    catch (ClassNotFoundException ex) {
      throw new IllegalStateException(
        "Cannot load optional framework class: " + PERSISTENCE_ANNOTATION_PROCESSOR_CLASS_NAME, ex);
    }
    def.setSource(source);
    beanDefs.add(registerPostProcessor(registry, def, PERSISTENCE_ANNOTATION_PROCESSOR_BEAN_NAME));
  }

  if (!registry.containsBeanDefinition(EVENT_LISTENER_PROCESSOR_BEAN_NAME)) {
    RootBeanDefinition def = new RootBeanDefinition(EventListenerMethodProcessor.class);
    def.setSource(source);
    beanDefs.add(registerPostProcessor(registry, def, EVENT_LISTENER_PROCESSOR_BEAN_NAME));
  }

  if (!registry.containsBeanDefinition(EVENT_LISTENER_FACTORY_BEAN_NAME)) {
    RootBeanDefinition def = new RootBeanDefinition(DefaultEventListenerFactory.class);
    def.setSource(source);
    beanDefs.add(registerPostProcessor(registry, def, EVENT_LISTENER_FACTORY_BEAN_NAME));
  }

  return beanDefs;
}
```

注册到 spring 容器并封装为BeanDefinitionHolder返回

```java
private static BeanDefinitionHolder registerPostProcessor(
  BeanDefinitionRegistry registry, RootBeanDefinition definition, String beanName) {

  definition.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
  registry.registerBeanDefinition(beanName, definition);
  return new BeanDefinitionHolder(definition, beanName);
}
```

## 构建ClassPathBeanDefinitionScanner对象

# 通过AnnotationConfigApplicationContext注册bean

AnnotationConfigApplicationContext对象初始化的时候也可以传入一个类的Class对象，进行注册

```java
public AnnotationConfigApplicationContext(Class<?>... annotatedClasses) {
    this();
    //将制定的类注册到工厂
    register(annotatedClasses);
    //触发容器解析注解
    refresh();
}
```

也可以在外部通过AnnotationConfigApplicationContext工厂类调用该方法注册bean，但是后面需要调用refresh去触发容器解析注解

```java
public void register(Class<?>... annotatedClasses) {
    Assert.notEmpty(annotatedClasses, "At least one annotated class must be specified");
    this.reader.register(annotatedClasses);
}
```

```java
public void register(Class<?>... annotatedClasses) {
    for (Class<?> annotatedClass : annotatedClasses) {
        registerBean(annotatedClass);
    }
}
```

注册指定的bean到工厂中，并且从注解中解析bean的元数据

```java
<T> void doRegisterBean(Class<T> annotatedClass, @Nullable Supplier<T> instanceSupplier, @Nullable String name,@Nullable Class<? extends Annotation>[] qualifiers, BeanDefinitionCustomizer... definitionCustomizers) {

    //构建AnnotatedGenericBeanDefinition包含了类的一些元信息比如scope，lazy等等
    AnnotatedGenericBeanDefinition abd = new AnnotatedGenericBeanDefinition(annotatedClass);

    //基于@Conditional注解判断是否需要跳过解析
    if (this.conditionEvaluator.shouldSkip(abd.getMetadata())) {
        return;
    }

    abd.setInstanceSupplier(instanceSupplier);

    //解析注解上的Scope得到类的作用域
    ScopeMetadata scopeMetadata = this.scopeMetadataResolver.resolveScopeMetadata(abd);

    //设置类的作用于
    abd.setScope(scopeMetadata.getScopeName());

    //beanNameGenerator生成bean的名称，如果有在@component上指定则取该值，否则默认是类名第一个字母小写
    String beanName = (name != null ? name : this.beanNameGenerator.generateBeanName(abd, this.registry));

    //解析Lazy DependsOn Primary Role等等注解，并设置到元数据中
    AnnotationConfigUtils.processCommonDefinitionAnnotations(abd);

    //这里涉及到自动装配
    if (qualifiers != null) {
        for (Class<? extends Annotation> qualifier : qualifiers) {
            //如果配置了@Primary注解，如果加了则作为首选
            if (Primary.class == qualifier) {
                abd.setPrimary(true);
            }
            //懒加载，前面加过
            else if (Lazy.class == qualifier) {
                abd.setLazyInit(true);
            }
            else {
                //如果使用了除@Primary和@Lazy以外的其他注解，则为该Bean添加一个根据名字自动装配的限定符
                abd.addQualifier(new AutowireCandidateQualifier(qualifier));
            }
        }
    }

    //对BeanDefinition做一些定制化的回调
    for (BeanDefinitionCustomizer customizer : definitionCustomizers) {
        customizer.customize(abd);
    }

    //对BeanDefinition和beanName做一个简单封装
    BeanDefinitionHolder definitionHolder = new BeanDefinitionHolder(abd, beanName);

    //ScopedProxyMode跟SpringMVC相关
    definitionHolder = AnnotationConfigUtils.applyScopedProxyMode(scopeMetadata, definitionHolder, this.registry);

    //注册到spring工厂中
    BeanDefinitionReaderUtils.registerBeanDefinition(definitionHolder, this.registry);
}
```

最底层的注册是将BeanDefinition注册到刚开始初始化的DefaultListableBeanFactory中

```java
public static void registerBeanDefinition(
    BeanDefinitionHolder definitionHolder, BeanDefinitionRegistry registry)
    throws BeanDefinitionStoreException {

    //将bean注册到DefaultListableBeanFactory工厂中
    // Register bean definition under primary name.
    String beanName = definitionHolder.getBeanName();
    registry.registerBeanDefinition(beanName, definitionHolder.getBeanDefinition());

    //如果存在别名，同样进行注册
    // Register aliases for bean name, if any.
    String[] aliases = definitionHolder.getAliases();
    if (aliases != null) {
        for (String alias : aliases) {
            registry.registerAlias(beanName, alias);
        }
    }
}
```

# refresh触发容器解析

