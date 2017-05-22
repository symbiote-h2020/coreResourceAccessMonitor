package eu.h2020.symbiote.cram;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Bean;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;

import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.rabbit.AsyncRabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;

import org.springframework.data.mongodb.core.geo.GeoJsonModule;
import com.fasterxml.jackson.databind.ObjectMapper;

import eu.h2020.symbiote.security.InternalSecurityHandler;
import eu.h2020.symbiote.security.session.AAM;
import eu.h2020.symbiote.security.exceptions.SecurityHandlerException;

import java.util.List;
import java.util.HashMap;
import java.util.Iterator;


@EnableDiscoveryClient
@EnableRabbit
@SpringBootApplication
public class CoreResourceAccessMonitorApplication {

	private static Log log = LogFactory.getLog(CoreResourceAccessMonitorApplication.class);

    @Value("${rabbit.host}") 
    private String rabbitMQHostIP;

    @Value("${rabbit.username}") 
    private String rabbitMQUsername;

    @Value("${rabbit.password}") 
    private String rabbitMQPassword;

    @Value("${symbiote.coreaam.url}") 
    private String symbioteCoreInterfaceAddress;

	public static void main(String[] args) {
		SpringApplication.run(CoreResourceAccessMonitorApplication.class, args);
    }

    @Bean
    public AlwaysSampler defaultSampler() {
        return new AlwaysSampler();
    }


    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setConcurrentConsumers(3);
        factory.setMaxConcurrentConsumers(10);
        factory.setMessageConverter(jackson2JsonMessageConverter());
        return factory;
    }

    @Bean 
    Jackson2JsonMessageConverter jackson2JsonMessageConverter() {

        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();

        /**
         * It is necessary to register the GeoJsonModule, otherwise the GeoJsonPoint cannot
         * be deserialized by Jackson2JsonMessageConverter.
         */
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new GeoJsonModule());
        converter.setJsonObjectMapper(mapper);
        return converter;
    }


    @Bean
    public ConnectionFactory connectionFactory() throws Exception {
        CachingConnectionFactory connectionFactory = new CachingConnectionFactory(rabbitMQHostIP);
        // connectionFactory.setPublisherConfirms(true);
        // connectionFactory.setPublisherReturns(true);
        connectionFactory.setUsername(rabbitMQUsername);
        connectionFactory.setPassword(rabbitMQPassword);
        return connectionFactory;
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, Jackson2JsonMessageConverter jackson2JsonMessageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jackson2JsonMessageConverter);
        return rabbitTemplate;
    }

    @Bean
    public AsyncRabbitTemplate asyncRabbitTemplate(RabbitTemplate rabbitTemplate) {

       /**
        * The following AsyncRabbitTemplate constructor uses "Direct replyTo" for replies.
        */
        AsyncRabbitTemplate asyncRabbitTemplate = new AsyncRabbitTemplate(rabbitTemplate);
        asyncRabbitTemplate.setReceiveTimeout(5000);

        return asyncRabbitTemplate;
    }

    @Bean HashMap<String, AAM> aamsMap() {
        return new HashMap<String, AAM>();
    }

    @Bean
    public InternalSecurityHandler securityHandler(HashMap<String, AAM> aamsMap) {

        InternalSecurityHandler securityHandler = new InternalSecurityHandler(symbioteCoreInterfaceAddress, rabbitMQHostIP, rabbitMQUsername, rabbitMQPassword);
        return securityHandler;
    }
}