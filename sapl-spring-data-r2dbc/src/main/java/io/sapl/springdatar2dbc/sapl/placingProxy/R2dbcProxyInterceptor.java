package io.sapl.springdatar2dbc.sapl.placingProxy;

import io.sapl.springdatar2dbc.sapl.Enforce;
import io.sapl.springdatar2dbc.sapl.QueryManipulationEnforcementData;
import io.sapl.springdatar2dbc.sapl.QueryManipulationEnforcementPointFactory;
import io.sapl.springdatar2dbc.sapl.SaplProtected;
import io.sapl.springdatar2dbc.sapl.handlers.AuthorizationSubscriptionHandlerProvider;
import io.sapl.springdatar2dbc.sapl.utils.Utilities;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.PolicyDecisionPoint;
import lombok.SneakyThrows;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.lang.reflect.*;
import java.util.*;
import java.util.logging.Logger;

/**
 * This service is the gathering point of all SaplEnforcementPoints for the
 * MongoDb database type. A {@link MethodInterceptor} is needed to manipulate
 * the database method query. At the beginning the corresponding
 * {@link AuthorizationSubscription} is searched for and built together if
 * necessary. Afterwards between 4 scenarios is differentiated and acted
 * accordingly.
 * <p>
 * Scenario 1: The method of the repository, which is to be executed, has a
 * {@link Query} annotation.
 * <p>
 * 2nd scenario: The method of the repository, which should be executed, is a
 * Spring Data JPA query method.
 * <p>
 * 3rd scenario: Neither of the above scenarios is the case, but an
 * AuthorizationSubscription has been found and thus manipulation of the data is
 * desired.
 * <p>
 * 4th scenario: None of the above scenarios are true and the execution of the
 * database method is forwarded.
 */
@Service
public class R2dbcProxyInterceptor<T> implements MethodInterceptor {
    private final Logger                                   logger = Logger
            .getLogger(R2dbcProxyInterceptor.class.getName());
    private final AuthorizationSubscriptionHandlerProvider authSubHandler;
    private final QueryManipulationEnforcementData<T>      enforcementData;
    private final QueryManipulationEnforcementPointFactory factory;

    public R2dbcProxyInterceptor(AuthorizationSubscriptionHandlerProvider authSubHandler, BeanFactory beanFactory,
            PolicyDecisionPoint pdp, QueryManipulationEnforcementPointFactory factory) {
        this.authSubHandler  = authSubHandler;
        this.factory         = factory;
        this.enforcementData = new QueryManipulationEnforcementData<>(null, beanFactory, null, pdp, null);
    }

    @SneakyThrows
    public Object invoke(MethodInvocation methodInvocation) {

        var repositoryMethod = methodInvocation.getMethod();
        var repository       = methodInvocation.getMethod().getDeclaringClass();

        if (hasAnnotationSaplProtected(repository) || hasAnnotationSaplProtected(repositoryMethod)
                || hasAnnotationEnforce(repositoryMethod)) {

            var returnClassOfMethod = Objects.requireNonNull(repositoryMethod).getReturnType();
            var authSub             = this.authSubHandler.getAuthSub(repository, methodInvocation);

            if (authSub == null) {
                logger.warning(
                        "The Sapl implementation for the manipulation of the database queries was recognised, but no AuthorizationSubscription was found.");
                return methodInvocation.proceed();
            }

            @SuppressWarnings("unchecked")
            var domainType = (Class<T>) ((ParameterizedType) repository.getGenericInterfaces()[0])
                    .getActualTypeArguments()[0];

            enforcementData.setMethodInvocation(methodInvocation);
            enforcementData.setDomainType(domainType);
            enforcementData.setAuthSub(authSub);

            /*
             * Introduce RelationalAtQueryImplementation if method has @Query-Annotation.
             */
            if (hasAnnotationQuery(repositoryMethod)) {
                logger.info(Utilities.STRING_BASED_IMPL_MSG);

                var annotationQueryEnforcementPoint = factory
                        .createR2dbcAnnotationQueryManipulationEnforcementPoint(enforcementData);

                return convertReturnTypeIfNecessary(annotationQueryEnforcementPoint.enforce(), returnClassOfMethod);
            }

            /*
             * Introduce MethodBasedImplementation if the query can be derived from the name
             * of the method.
             */
            if (Utilities.isMethodNameValid(repositoryMethod.getName())) {
                logger.info(Utilities.METHOD_BASED_IMPL_MSG);

                var methodNameQueryEnforcementPoint = factory
                        .createR2dbcMethodNameQueryManipulationEnforcementPoint(enforcementData);

                return convertReturnTypeIfNecessary(methodNameQueryEnforcementPoint.enforce(), returnClassOfMethod);
            }

            /*
             * Introduce ProceededDataFilterEnforcementPoint if none of the previous cases
             * apply. The requested method is executed and the corresponding condition from
             * the obligation are applied to the received data.
             */
            if (!Utilities.isMethodNameValid(repositoryMethod.getName()) && !hasAnnotationQuery(repositoryMethod)) {
                logger.info(Utilities.FILTER_BASED_IMPL_MSG);

                var filterEnforcementPoint = factory.createProceededDataFilterEnforcementPoint(enforcementData);

                return convertReturnTypeIfNecessary(filterEnforcementPoint.enforce(), returnClassOfMethod);
            }

        }

        /*
         * If no filtering or transforming of the data is desired, the call to the
         * method is merely forwarded.
         */
        return methodInvocation.proceed();
    }

    /**
     * To avoid duplicate code and for simplicity, fluxes were used in all
     * EnforcementPoints, even if the database method expects a mono. Therefore, at
     * this point it must be checked here what the return type is and transformed
     * accordingly. In addition, the case that a non-reactive type, such as a list
     * or collection, is expected is also covered.
     *
     * @param databaseObjects     are the already manipulated objects, which are
     *                            queried with the manipulated query.
     * @param returnClassOfMethod is the type which the database method expects as
     *                            return type.
     * @return the manipulated objects transformed to the correct type accordingly.
     */
    @SneakyThrows
    @SuppressWarnings("ReactiveStreamsUnusedPublisher")
    private Object convertReturnTypeIfNecessary(Flux<T> databaseObjects, Class<?> returnClassOfMethod) {
        if (Utilities.isFlux(returnClassOfMethod)) {
            return databaseObjects;
        }

        if (Utilities.isMono(returnClassOfMethod)) {
            return databaseObjects.next();
        }

        if (Utilities.isListOrCollection(returnClassOfMethod)) {
            return databaseObjects.collectList().toFuture().get();
        }

        throw new ClassNotFoundException("Return type of method not supported: " + returnClassOfMethod);
    }

    /**
     * Checks whether a method has a {@link Query} annotation.
     *
     * @param method is the method to be checked.
     * @return true, if method has a {@link Query} annotation.
     */
    private boolean hasAnnotationQuery(Method method) {
        return method.isAnnotationPresent(Query.class);
    }

    /**
     * Checks whether a method has a {@link SaplProtected} annotation.
     *
     * @param method is the method to be checked.
     * @return true, if method has a {@link SaplProtected} annotation.
     */
    private boolean hasAnnotationSaplProtected(Method method) {
        return method.isAnnotationPresent(SaplProtected.class);
    }

    /**
     * Checks whether a method has a {@link SaplProtected} annotation.
     *
     * @param clazz is the class to be checked.
     * @return true, if method has a {@link SaplProtected} annotation.
     */
    private boolean hasAnnotationSaplProtected(Class<?> clazz) {
        return clazz.isAnnotationPresent(SaplProtected.class);
    }

    /**
     * Checks whether a method has a {@link Enforce} annotation.
     *
     * @param method is the method to be checked.
     * @return true, if method has a {@link Enforce} annotation.
     */
    private boolean hasAnnotationEnforce(Method method) {
        return method.isAnnotationPresent(Enforce.class);
    }
}
