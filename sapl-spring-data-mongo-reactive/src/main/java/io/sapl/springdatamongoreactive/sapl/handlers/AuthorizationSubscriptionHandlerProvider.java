package io.sapl.springdatamongoreactive.sapl.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import io.sapl.api.pdp.AuthorizationSubscription;
import lombok.SneakyThrows;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.logging.Logger;

/**
 * This service takes care of obtaining the AuthorizationSubscription to execute
 * the desired policies.
 * <p>
 * First, the EnforceAnnotationHandler is used to check whether the method to be
 * executed has an Enforce annotation. If this is the case, all available
 * attributes (subject, action, resource, environment) specified in the
 * annotation are collected.
 * <p>
 * In the case that not all four attributes were specified in the annotation, a
 * bean is searched for, which fills the remaining attributes. This means that
 * the attributes from the annotation are preferred for the
 * AuthorizationSubscription if, for example, an attribute was specified twice.
 * In order to offer the AuthorizationSubscription as a bean, the bean must have
 * a specific name in order to be successfully found from the BeanFactory.
 * <p>
 * The name for a bean to protect a specific method is composed of the name of
 * the method and the name of the repository. Example:
 * <p>
 * Method name: findAllByName Repository name: ReactiveMongoUserRepository Name
 * of the bean: findAllByNameReactiveMongoUserRepository
 * <p>
 * If no bean is found for the specific method, a general bean is searched for
 * the whole repository. The name of the general bean is composed of
 * generalProtection and the name of the repository.
 * <p>
 * However, no error is thrown if, for example, an attribute is missing, no
 * annotation is available or no bean was found at all. The user is made
 * attentive only by a message of the logger on it.
 */
@Service
public class AuthorizationSubscriptionHandlerProvider {
    private static final String            BEAN_FOUND_MESSAGE     = "Bean to receive specific AuthorizationSubscription Found: ";
    private static final String            GENERAL_PROTECTION     = "generalProtection";
    private static final String            NO_AUTH_SUB_FOUND1     = "The database method ";
    private static final String            AUTH_SUB_IN_USE        = "Sapl is using following AuthSub: ";
    private static final String            BEAN_NOT_FOUND_MESSAGE = "General repository protection via bean: ";
    private static final String            NO_AUTH_SUB_FOUND2     = " received no AuthorizationSubscription. ";
    private final BeanFactory              beanFactory;
    private final EnforceAnnotationHandler enforceAnnotationHandler;
    private final Logger                   logger                 = Logger
            .getLogger(AuthorizationSubscriptionHandlerProvider.class.getName());

    @Autowired
    public AuthorizationSubscriptionHandlerProvider(BeanFactory beanFactory,
            EnforceAnnotationHandler enforceAnnotationHandler) {
        this.beanFactory              = beanFactory;
        this.enforceAnnotationHandler = enforceAnnotationHandler;
    }

    /**
     * Initiates finding the corresponding {@link AuthorizationSubscription}. First,
     * the {@link EnforceAnnotationHandler} is used to search for an
     * AuthorizationSubscription and for reasons of possible merging then the
     * {@link BeanFactory} is used to search for a matching bean.
     *
     * @param repoClass        represents the repository class.
     * @param methodInvocation of interface
     *                         {@link org.aopalliance.intercept.MethodInterceptor}
     * @return the found AuthorizationSubscription
     */
    @SneakyThrows
    public AuthorizationSubscription getAuthSub(Class<?> repoClass, MethodInvocation methodInvocation) {

        Assert.isTrue(repoClass.isInterface(), "Repository is no interface.");

        var annotationBased = enforceAnnotationHandler.enforceAnnotation(methodInvocation);

        if (annotationBased != null && authSubIsComplete(annotationBased)) {
            logger.info(AUTH_SUB_IN_USE + annotationBased);
            return annotationBased;
        }

        var staticBeanBased = getAuthorizationSubscriptionByBean(methodInvocation.getMethod().getName(),
                repoClass.getSimpleName());

        if (annotationBased != null && staticBeanBased != null) {
            return mergeTwoAuthSubs(annotationBased, staticBeanBased);
        } else {
            logger.info(AUTH_SUB_IN_USE + staticBeanBased);
            return staticBeanBased;
        }
    }

    /**
     * Searches for a bean with a specific name using the {@link BeanFactory} to get
     * a {@link AuthorizationSubscription}.
     *
     * @param methodName is the name of the repository method.
     * @param repoName   is the name of the repository.
     * @return the found AuthorizationSubscription.
     */
    private AuthorizationSubscription getAuthorizationSubscriptionByBean(String methodName, String repoName) {
        AuthorizationSubscription authorizationSubscription = null;
        try {
            var bean = methodName + repoName;
            authorizationSubscription = (AuthorizationSubscription) beanFactory.getBean(bean);
            logger.info(BEAN_FOUND_MESSAGE + bean);
        } catch (NoSuchBeanDefinitionException e) {
            var bean = GENERAL_PROTECTION + repoName;
            try {
                authorizationSubscription = (AuthorizationSubscription) beanFactory.getBean(bean);
                logger.info(BEAN_NOT_FOUND_MESSAGE + bean);
            } catch (NoSuchBeanDefinitionException er) {
                logger.warning(NO_AUTH_SUB_FOUND1 + repoName + "." + methodName + NO_AUTH_SUB_FOUND2);
            }
        }
        return authorizationSubscription;
    }

    /**
     * Merges two {@link AuthorizationSubscription} and prefers the
     * AuthorizationSubscription specified as first parameter.
     *
     * @param firstAuthSub  is the preferred AuthorizationSubscription.
     * @param secondAuthSub fills the first AuthorizationSubscription.
     * @return merged AuthorizationSubscription.
     */
    private AuthorizationSubscription mergeTwoAuthSubs(AuthorizationSubscription firstAuthSub,
            AuthorizationSubscription secondAuthSub) {
        JsonNode subject;
        JsonNode action;
        JsonNode resource;
        JsonNode environment;

        if (isNotNullOrEmpty(firstAuthSub.getSubject())) {
            subject = firstAuthSub.getSubject();
        } else
            subject = secondAuthSub.getSubject();

        if (isNotNullOrEmpty(firstAuthSub.getAction())) {
            action = firstAuthSub.getAction();
        } else
            action = secondAuthSub.getAction();

        if (isNotNullOrEmpty(firstAuthSub.getResource())) {
            resource = firstAuthSub.getResource();
        } else
            resource = secondAuthSub.getResource();

        if (isNotNullOrEmpty(firstAuthSub.getEnvironment())) {
            environment = firstAuthSub.getEnvironment();
        } else
            environment = secondAuthSub.getEnvironment();

        var authSub = new AuthorizationSubscription(subject, action, resource, environment);
        logger.info(AUTH_SUB_IN_USE + authSub);

        return authSub;
    }

    /**
     * Checks whether a {@link JsonNode} is blank or null.
     *
     * @param node is the corresponding JsonNode object.
     * @return true, if the JsonNode object is not null or empty.
     */
    private boolean isNotNullOrEmpty(JsonNode node) {
        var nodeIsNotNullOrEmpty = !node.isNull();

        if (node.asText().isEmpty()) {
            nodeIsNotNullOrEmpty = false;
        }

        return nodeIsNotNullOrEmpty;
    }

    /**
     * Checks if an {@link AuthorizationSubscription} has all attributes.
     *
     * @param authSub is the corresponding AuthorizationSubscription.
     * @return true, if all attributes have values.
     */
    private boolean authSubIsComplete(AuthorizationSubscription authSub) {
        return isNotNullOrEmpty(authSub.getSubject()) && isNotNullOrEmpty(authSub.getAction())
                && isNotNullOrEmpty(authSub.getResource()) && isNotNullOrEmpty(authSub.getEnvironment());
    }
}
