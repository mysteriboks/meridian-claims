package com.meridian.claims.job;

import org.quartz.spi.TriggerFiredBundle;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.scheduling.quartz.SpringBeanJobFactory;

/**
 * Quartz instantiates Job classes itself via newInstance(), so the stock
 * {@link SpringBeanJobFactory} leaves their {@code @Autowired}/{@code @Value}
 * fields null. This subclass runs the Spring autowiring pass on every freshly
 * created job instance, so jobs like {@link SlaEscalationJob} get their DAOs and
 * services injected. Wired as the {@code jobFactory} on the SchedulerFactoryBean
 * in applicationContext.xml.
 */
public class AutowiringSpringBeanJobFactory extends SpringBeanJobFactory
        implements ApplicationContextAware {

    private AutowireCapableBeanFactory beanFactory;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.beanFactory = applicationContext.getAutowireCapableBeanFactory();
    }

    @Override
    protected Object createJobInstance(TriggerFiredBundle bundle) throws Exception {
        Object job = super.createJobInstance(bundle);
        beanFactory.autowireBean(job);
        return job;
    }
}
