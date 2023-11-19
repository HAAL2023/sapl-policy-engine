package io.sapl.springdatamongoreactive.sapl.queryTypes.methodNameEnforcement;

import com.fasterxml.jackson.databind.JsonNode;
import io.sapl.springdatamongoreactive.sapl.utils.SaplCondition;
import io.sapl.springdatamongoreactive.sapl.utils.SaplConditionOperation;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.repository.query.parser.PartTree;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * This class builds a query and is supported by the {@link PartTree} class,
 * among others. The query itself is created with the class {@link Criteria} at
 * the end. With the help of the {@link ReactiveMongoTemplate} it is possible to
 * communicate with the database in different ways.
 *
 * @param <T> is the domain type.
 */
public class SaplPartTreeCriteriaCreator<T> {

    private final Class<T> domainType;
    private final Object[] args;
    private final Method   repositoryMethod;

    private final MongoQueryCreatorFactory mongoQueryCreatorFactory;

    public SaplPartTreeCriteriaCreator(ReactiveMongoTemplate reactiveMongoTemplate, MethodInvocation methodInvocation,
            Class<T> domainType) {
        this.domainType       = domainType;
        this.repositoryMethod = methodInvocation.getMethod();
        this.args             = methodInvocation.getArguments();
        var partTree   = new PartTree(repositoryMethod.getName(), domainType);
        var repository = methodInvocation.getMethod().getDeclaringClass();
        this.mongoQueryCreatorFactory = new MongoQueryCreatorFactory(repository, reactiveMongoTemplate);
        this.mongoQueryCreatorFactory.createInstance(partTree, repositoryMethod, args);
    }

    /**
     * This is the entry method of the class and creates the {@link Query}. There
     * are several steps necessary until a query is created as a result in the end.
     * The parameters of the method must be put into a structured form. Also, the
     * conditions from the {@link io.sapl.api.pdp.Decision} are first packed into a
     * suitable form. The method name can then be adapted and a {@link Criteria} can
     * be built from all the information obtained.
     *
     * @param conditions are the query condition from the
     *                   {@link io.sapl.api.pdp.Decision}
     * @return a manipulated {@link Query}
     */
    public Query createManipulatedQuery(JsonNode conditions) {
        // Converts Parameters of the repository method to SaplConditions for further
        // operations.
        var saplParametersFromMethod = SaplConditionOperation.methodToSaplConditions(args, repositoryMethod,
                domainType);

        // Converts the conditions from the corresponding policy into SaplConditions for
        // further operations.
        var saplParametersFromObligation = SaplConditionOperation.jsonNodeToSaplConditions(conditions);

        // Creates a new method name from the old method name and the newly acquired
        // Sapl conditions.
        var modifiedMethodName = SaplConditionOperation.toModifiedMethodName(repositoryMethod.getName(),
                saplParametersFromObligation);

        // Create PartTree of new method name
        var manipulatedPartTree = new PartTree(modifiedMethodName, domainType);

        // Creates an object list of all values of all SaplParameters
        saplParametersFromMethod.addAll(saplParametersFromObligation);

        var allParameters = new ArrayList<>();
        for (SaplCondition parameter : saplParametersFromMethod) {
            allParameters.add(parameter.value());
        }

        var criteria = buildCriteria(manipulatedPartTree, allParameters);

        return (criteria == null ? new Query()
                : new Query(criteria).with(mongoQueryCreatorFactory.getConvertingParameterAccessor().getSort()));
    }

    /**
     * Builds a {@link Criteria} from a {@link PartTree} and certain parameters.
     * Actual query building logic. Traverses the {@link PartTree} and invokes
     * callback methods to delegate actual criteria creation and concatenation.
     *
     * @param manipulatedPartTree is the created PartTree of the manipulated method.
     * @param parameters          from the original method plus the parameters that
     *                            could be obtained from the condition from the
     *                            {@link io.sapl.api.pdp.Decision}.
     * @return a new {@link Criteria}.
     */
    private Criteria buildCriteria(PartTree manipulatedPartTree, List<Object> parameters) {
        Criteria base     = null;
        var      iterator = parameters.iterator();
        for (PartTree.OrPart node : manipulatedPartTree) {

            var parts = node.iterator();

            if (!parts.hasNext()) { // Don't know if this check is necessary, hard to test.
                throw new IllegalStateException(String.format("No part found in PartTree %s", manipulatedPartTree));
            }

            var criteria = mongoQueryCreatorFactory.create(parts.next(), iterator);

            while (parts.hasNext()) {
                criteria = mongoQueryCreatorFactory.and(parts.next(), criteria, iterator);
            }

            base = base == null ? criteria : mongoQueryCreatorFactory.or(base, criteria);
        }

        return base;
    }
}
