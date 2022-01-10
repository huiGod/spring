通过对象AnnotationConfigApplicationContext获取容器中的bean，调用的是DefaultListableBeanFactory默认工厂中的getBean方法

```java
@Override
public <T> T getBean(Class<T> requiredType, @Nullable Object... args) throws BeansException {
	//NamedBeanHolder 可以理解为一个数据结构和map差不多，里面就是存了bean的名字和bean的实例
    NamedBeanHolder<T> namedBean = resolveNamedBean(requiredType, args);
    if (namedBean != null) {
        return namedBean.getBeanInstance();
    }
    BeanFactory parent = getParentBeanFactory();
    if (parent != null) {
        return (args != null ? parent.getBean(requiredType, args) : parent.getBean(requiredType));
    }
    throw new NoSuchBeanDefinitionException(requiredType);
}
```

resolveNamedBean底层调用的是AbstractBeanFactory类的getBean方法

```java

```

