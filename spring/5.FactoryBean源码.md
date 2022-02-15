在Spring BeanFactory容器中管理两种bean  

1. 标准Java Bean  
2. 另一种是工厂Bean,   即实现了FactoryBean接口的bean  它不是一个简单的Bean 而是一个生产或修饰对象生成的工厂Bean

在 Spring 容器初始化的时候，会对所有对象进行实例化，如果是 FactoryBean 类型，则会对 BeanName 加上&前缀。后续在获取 FactoryBean 类型的对象时，如果通过默认的小写类名获取，则会是其 getObject 返回的对象；如果通过&加上小写类型获取，则才是 FactoryBean 类本身

```java
public interface FactoryBean<T> {

    //返回的对象实例
    T getObject() throws Exception;
    //Bean的类型
    Class<?> getObjectType();
    //true是单例，false是非单例  在Spring5.0中此方法利用了JDK1.8的新特性变成了default方法，返回true
    boolean isSingleton();
}
```

使用工厂上下文获取 FactoryBean 所产生的 bean 对象时，调用的 getBean 方法

```java
@Override
public <T> T getBean(Class<T> requiredType) throws BeansException {
  return getBean(requiredType, (Object[]) null);
}
@Override
public <T> T getBean(Class<T> requiredType, Object... args) throws BeansException {
  //解析Bean
  NamedBeanHolder<T> namedBean = resolveNamedBean(requiredType, args);
  if (namedBean != null) {
    return namedBean.getBeanInstance();
  }
  //如果当前Spring容器中没有获取到相应的Bean信息，则从父容器中获取
  //SpringMVC是一个很典型的父子容器
  BeanFactory parent = getParentBeanFactory();
  if (parent != null) {
    //一个重复的调用过程，只不过BeanFactory的实例变了
    return parent.getBean(requiredType, args);
  }
  //如果都没有获取到，则抛出异常
  throw new NoSuchBeanDefinitionException(requiredType);
}
```

```java
@Nullable
private <T> NamedBeanHolder<T> resolveNamedBean(Class<T> requiredType, @Nullable Object... args) throws BeansException {
  Assert.notNull(requiredType, "Required type must not be null");
  //这个方法是根据传入的Class类型来获取BeanName，因为我们有一个接口有多个实现类的情况(多态)，
  //所以这里返回的是一个String数组。这个过程也比较复杂
  String[] candidateNames = getBeanNamesForType(requiredType);
  //如果有多个BeanName，则挑选合适的BeanName
  if (candidateNames.length > 1) {
    List<String> autowireCandidates = new ArrayList<>(candidateNames.length);
    for (String beanName : candidateNames) {
      if (!containsBeanDefinition(beanName) || getBeanDefinition(beanName).isAutowireCandidate()) {
        autowireCandidates.add(beanName);
      }
    }
    if (!autowireCandidates.isEmpty()) {
      candidateNames = StringUtils.toStringArray(autowireCandidates);
    }
  }

  //如果只有一个BeanName 我们调用getBean方法来获取Bean实例来放入到NamedBeanHolder中
  //这里获取bean是根据beanName，beanType和args来获取bean
  if (candidateNames.length == 1) {
    String beanName = candidateNames[0];
    //这的getBean才是真正获取对象的方法
    return new NamedBeanHolder<>(beanName, getBean(beanName, requiredType, args));
  }
  //如果合适的BeanName还是有多个的话
  else if (candidateNames.length > 1) {
    Map<String, Object> candidates = new LinkedHashMap<>(candidateNames.length);
    for (String beanName : candidateNames) {
      //看看是不是已经创建多的单例Bean
      if (containsSingleton(beanName) && args == null) {
        Object beanInstance = getBean(beanName);
        candidates.put(beanName, (beanInstance instanceof NullBean ? null : beanInstance));
      }
      else {
        //调用getType方法继续获取Bean实例
        candidates.put(beanName, getType(beanName));
      }
    }
    //有多个Bean实例的话 则取带有Primary注解或者带有Primary信息的Bean
    String candidateName = determinePrimaryCandidate(candidates, requiredType);
    if (candidateName == null) {
      candidateName = determineHighestPriorityCandidate(candidates, requiredType);
    }
    if (candidateName != null) {
      //如果没有Primary注解或者Primary相关的信息，则去优先级高的Bean实例
      Object beanInstance = candidates.get(candidateName);
      //Class类型的话 继续调用getBean方法获取Bean实例
      if (beanInstance == null || beanInstance instanceof Class) {
        beanInstance = getBean(candidateName, requiredType, args);
      }
      return new NamedBeanHolder<>(candidateName, (T) beanInstance);
    }
    //都没有获取到 抛出异常
    throw new NoUniqueBeanDefinitionException(requiredType, candidates.keySet());
  }

  return null;
}
```

最终调用的是容器类中的getBean方法获取对象

```java
protected <T> T doGetBean(final String name, @Nullable final Class<T> requiredType,
			@Nullable final Object[] args, boolean typeCheckOnly) throws BeansException {

		//FactoryBean本身的bean是以&beanName命名的，这里需要将&去掉
		final String beanName = transformedBeanName(name);
		Object bean;

		// Eagerly check singleton cache for manually registered singletons.
		//先尝试从缓存中获取对象，获取不到才会执行后续的创建流程
		//获取到的对象有两种场景
		//一种是bean创建完成存储到最终的缓存中
		//另一种是未创建完成，但是会预存到一个单独的缓存中，这种情况是针对可能存在的循环引用进行处理

		//如A引用B，B又引用了A，因而在初始化A时，A会先调用构造函数创建出一个实例，在依赖注入B之前，先将A实例缓存起来
		//然后在初始化A时，依赖注入阶段，会触发初始化B，B创建后需要依赖注入A时，先从缓存中获取A（这个时候的A是不完整的)，避免循环依赖的问题出现。
		Object sharedInstance = getSingleton(beanName);
		if (sharedInstance != null && args == null) {
			if (logger.isDebugEnabled()) {
				if (isSingletonCurrentlyInCreation(beanName)) {
					logger.debug("Returning eagerly cached instance of singleton bean '" + beanName +
							"' that is not fully initialized yet - a consequence of a circular reference");
				}
				else {
					logger.debug("Returning cached instance of singleton bean '" + beanName + "'");
				}
			}

			//如果 sharedInstance 是普通的单例 bean，下面的方法会直接返回。但如果
			//sharedInstance 是 FactoryBean 类型的，则需调用 getObject 工厂方法获取真正的
			//bean 实例。如果用户想获取 FactoryBean 本身，这里也不会做特别的处理，直接返回
			//即可。毕竟 FactoryBean 的实现类本身也是一种 bean，只不过具有一点特殊的功能而已。
			bean = getObjectForBeanInstance(sharedInstance, name, beanName, null);
		}
  ...
}
```

最重要的是通过getObjectForBeanInstance方法来处理 FactoryBean

```java
protected Object getObjectForBeanInstance(
  Object beanInstance, String name, String beanName, @Nullable RootBeanDefinition mbd) {

  // Don't let calling code try to dereference the factory if the bean isn't a factory.
  //判断bean 名称是否以&开头
  if (BeanFactoryUtils.isFactoryDereference(name)) {
    if (beanInstance instanceof NullBean) {
      return beanInstance;
    }
    //以&开头的 bean 一定要是FactoryBean类型
    if (!(beanInstance instanceof FactoryBean)) {
      throw new BeanIsNotAFactoryException(transformedBeanName(name), beanInstance.getClass());
    }
  }

  // Now we have the bean instance, which may be a normal bean or a FactoryBean.
  // If it's a FactoryBean, we use it to create a bean instance, unless the
  // caller actually wants a reference to the factory.
  //如果对象不是FactoryBean类型则直接返回
  //或者获取的 bean 名称以&开头，说明需要获取的是FactoryBean本身对象，也直接返回
  if (!(beanInstance instanceof FactoryBean) || BeanFactoryUtils.isFactoryDereference(name)) {
    return beanInstance;
  }

  Object object = null;
  if (mbd == null) {
    //后续直接从缓存获取 factoryBean生成的对象
    object = getCachedObjectForFactoryBean(beanName);
  }
  if (object == null) {
    // Return bean instance from factory.
    FactoryBean<?> factory = (FactoryBean<?>) beanInstance;
    // Caches object obtained from FactoryBean if it is a singleton.
    if (mbd == null && containsBeanDefinition(beanName)) {
      mbd = getMergedLocalBeanDefinition(beanName);
    }
    boolean synthetic = (mbd != null && mbd.isSynthetic());
    // FactoryBean getObject触发   并缓存到factoryBeanObjectCache集合中
    object = getObjectFromFactoryBean(factory, beanName, !synthetic);
  }
  return object;
}
```

