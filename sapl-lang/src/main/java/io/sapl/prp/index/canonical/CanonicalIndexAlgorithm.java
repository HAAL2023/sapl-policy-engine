/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.sapl.prp.index.canonical;

import com.google.common.collect.Sets;
import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.prp.PolicyRetrievalResult;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;

@Slf4j
@UtilityClass
public class CanonicalIndexAlgorithm {

    public Mono<PolicyRetrievalResult> match(EvaluationContext subscriptionScopedEvaluationCtx, CanonicalIndexDataContainer dataContainer) {
                return matchCollector(subscriptionScopedEvaluationCtx, dataContainer);
//        return matchAccumulate(subscriptionScopedEvaluationCtx, dataContainer);
    }

    public Mono<PolicyRetrievalResult> matchCollector(EvaluationContext subscriptionScopedEvaluationCtx, CanonicalIndexDataContainer dataContainer) {
        log.info("match using collector");
        var initialMatchingCtx = new CanonicalIndexMatchingContext(dataContainer.getNumberOfConjunctions(), subscriptionScopedEvaluationCtx);

        var monoList = dataContainer.getPredicateOrder().stream()
                .map(predicate -> predicate.evaluate(initialMatchingCtx.getSubscriptionScopedEvaluationContext())
                        .map(val -> Pair.of(predicate, val))
                ).collect(Collectors.toList());

        var fluxOfPairs = Flux.concat(monoList);
        var matchingCtxMono = fluxOfPairs.collect(MatchingContextCollector.toResult(initialMatchingCtx, dataContainer));

        return matchingCtxMono.map(matchingCtx -> {
            var matching = matchingCtx.getMatchingCandidatesMask();
            var formulas = fetchFormulas(matching, dataContainer);
            var policies = fetchPolicies(formulas, dataContainer);

            return new PolicyRetrievalResult(policies, matchingCtx.isErrorsInTargets(), true);
        }).onErrorReturn(new PolicyRetrievalResult(Collections.emptyList(), true, true));
    }

    public Mono<PolicyRetrievalResult> matchAccumulate(EvaluationContext subscriptionScopedEvaluationCtx, CanonicalIndexDataContainer dataContainer) {
        log.info("match using accumulate");
        var matchingCtxMono = Mono.just(new CanonicalIndexMatchingContext(dataContainer.getNumberOfConjunctions(), subscriptionScopedEvaluationCtx));

        for (Predicate predicate : dataContainer.getPredicateOrder()) {
            matchingCtxMono = matchingCtxMono.flatMap(matchingContext -> accumulate(matchingContext, predicate, dataContainer));
        }

        return matchingCtxMono.map(matchingCtx -> {
            var matching = matchingCtx.getMatchingCandidatesMask();
            var formulas = fetchFormulas(matching, dataContainer);
            var policies = fetchPolicies(formulas, dataContainer);

            return new PolicyRetrievalResult(policies, matchingCtx.isErrorsInTargets(), true);
        }).onErrorReturn(new PolicyRetrievalResult(Collections.emptyList(), true, true));
    }

    private Mono<CanonicalIndexMatchingContext> accumulate(CanonicalIndexMatchingContext matchingCtx,
                                                           Predicate predicate, CanonicalIndexDataContainer dataContainer) {
        if (!matchingCtx.isPredicateReferencedInCandidates(predicate))
            return Mono.just(matchingCtx);

        return predicate.evaluate(matchingCtx.getSubscriptionScopedEvaluationContext())
                .map(CanonicalIndexAlgorithm.handleEvaluationResult(matchingCtx, predicate, dataContainer));
    }

    static Function<Val, CanonicalIndexMatchingContext> handleEvaluationResult(
            CanonicalIndexMatchingContext matchingCtx, Predicate predicate, CanonicalIndexDataContainer dataContainer) {
        return evaluationResult -> {
            if (evaluationResult.isError()) {
                handleErrorEvaluationResult(predicate, matchingCtx);
            } else {
                updateCandidatesInMatchingContext(predicate, evaluationResult.getBoolean(), matchingCtx, dataContainer);
            }
            return matchingCtx;
        };
    }

    Bitmask orBitMask(@NonNull Bitmask b1, @NonNull Bitmask b2) {
        var result = new Bitmask(b1);
        result.or(b2);
        return result;
    }

    private void updateCandidatesInMatchingContext(Predicate predicate, Boolean evaluationResult,
                                                   CanonicalIndexMatchingContext matchingCtx, CanonicalIndexDataContainer dataContainer) {

        var satisfiedCandidates = findSatisfiableCandidates(predicate, evaluationResult, matchingCtx, dataContainer);
        // add satisfied candidates to mask of matching candidates
        matchingCtx.addSatisfiedCandidates(satisfiedCandidates);

        var unsatisfiedCandidates = findUnsatisfiableCandidates(matchingCtx, predicate, evaluationResult);

        var orphanedCandidates = findOrphanedCandidates(satisfiedCandidates, matchingCtx, dataContainer);

        reduceCandidates(matchingCtx, unsatisfiedCandidates, satisfiedCandidates, orphanedCandidates);
    }

    void handleErrorEvaluationResult(final Predicate predicate, CanonicalIndexMatchingContext matchingCtx) {
        matchingCtx.setErrorsInTargets(true);
        // remove all conjunctions used by the predicate that returned an error during
        matchingCtx.removeCandidates(predicate.getConjunctions());
    }

    Bitmask findOrphanedCandidates(final Bitmask satisfiableCandidates, CanonicalIndexMatchingContext matchingCtx,
                                   CanonicalIndexDataContainer dataContainer) {
        var result = new Bitmask();

        satisfiableCandidates.forEachSetBit(index -> {
            var cTuples = dataContainer.getConjunctionsInFormulasReferencingConjunction(index);
            for (CTuple cTuple : cTuples) {
                if (!matchingCtx.isRemainingCandidate(cTuple.getCI()))
                    continue;

                matchingCtx.increaseNumberOfEliminatedFormulasForConjunction(cTuple.getCI(), cTuple.getN());

                // if all formulas of conjunction have been eliminated
                if (matchingCtx.areAllFunctionsEliminated(cTuple.getCI(),
                        dataContainer.getNumberOfFormulasWithConjunction(cTuple.getCI()))) {
                    result.set(cTuple.getCI());
                }

            }
        });

        return result;
    }

    void reduceCandidates(final CanonicalIndexMatchingContext matchingCtx, final Bitmask unsatisfiedCandidates,
                          final Bitmask satisfiedCandidates, final Bitmask orphanedCandidates) {
        matchingCtx.removeCandidates(unsatisfiedCandidates);
        matchingCtx.removeCandidates(satisfiedCandidates);
        matchingCtx.removeCandidates(orphanedCandidates);
    }

    Set<DisjunctiveFormula> fetchFormulas(final Bitmask satisfiableCandidates,
                                          CanonicalIndexDataContainer dataContainer) {
        final Set<DisjunctiveFormula> result = new HashSet<>();
        satisfiableCandidates.forEachSetBit(index -> result.addAll(dataContainer.getRelatedFormulas(index)));
        return result;
    }

    Bitmask findSatisfiableCandidates(final Predicate predicate, final boolean evaluationResult,
                                      CanonicalIndexMatchingContext matchingCtx, CanonicalIndexDataContainer dataContainer) {
        var result = new Bitmask();
        // calling method with negated evaluation result will return satisfied clauses
        var satisfiableCandidates = findUnsatisfiableCandidates(matchingCtx, predicate, !evaluationResult);

        satisfiableCandidates.forEachSetBit(index -> {
            // increment number of true literals
            matchingCtx.incrementTrueLiteralsForConjunction(index);

            // if all literals in conjunction are true, add conjunction to result
            if (matchingCtx.isConjunctionSatisfied(index, dataContainer.getNumberOfLiteralsInConjunction(index)))
                result.set(index);
        });

        return result;
    }

    private Set<SAPL> fetchPolicies(final Set<DisjunctiveFormula> formulas, CanonicalIndexDataContainer dataContainer) {
        return formulas.parallelStream().map(dataContainer::getPoliciesIncludingFormula)
                .flatMap(Collection::parallelStream).collect(Collectors.toSet());
    }

    Bitmask findUnsatisfiableCandidates(final CanonicalIndexMatchingContext matchingCtx, final Predicate predicate,
                                        final boolean predicateEvaluationResult) {
        var result = matchingCtx.getCopyOfCandidates();

        if (predicateEvaluationResult)
            result.and(predicate.getFalseForTruePredicate());
        else
            result.and(predicate.getFalseForFalsePredicate());

        return result;
    }

    @RequiredArgsConstructor
    public static class MatchingContextCollector<T extends Pair<Predicate, Val>> implements Collector<T, Builder, CanonicalIndexMatchingContext> {

        private final CanonicalIndexMatchingContext initialContext;
        private final CanonicalIndexDataContainer dataContainer;

        @Override
        public Supplier<Builder> supplier() {
            return () -> Builder.builder(initialContext, dataContainer);
        }

        @Override
        public BiConsumer<Builder, T> accumulator() {
            return Builder::add;
        }

        @Override
        public BinaryOperator<Builder> combiner() {
            return (left, right) -> left.combine(right.build());
        }

        @Override
        public Function<Builder, CanonicalIndexMatchingContext> finisher() {
            return Builder::build;
        }

        @Override
        public Set<Characteristics> characteristics() {
            return Sets.immutableEnumSet(Characteristics.UNORDERED);
        }

        public static <T extends Pair<Predicate, Val>> MatchingContextCollector<T> toResult(CanonicalIndexMatchingContext initialContext,
                                                                                            CanonicalIndexDataContainer dataContainer) {
            return new MatchingContextCollector<>(initialContext, dataContainer);
        }
    }

    @RequiredArgsConstructor
    private static class Builder {

        private final CanonicalIndexMatchingContext matchingCtx;
        private final CanonicalIndexDataContainer dataContainer;

        public static Builder builder(CanonicalIndexMatchingContext initial, CanonicalIndexDataContainer dataContainer) {
            return new Builder(initial, dataContainer);
        }

        public static void add(Builder policyRetrievalResultBuilder, Pair<Predicate, Val> pair) {
            policyRetrievalResultBuilder.addPair(pair);
        }

        private void addPair(Pair<Predicate, Val> pair) {
            var predicate = pair.getKey();
            var evaluationResult = pair.getValue();

            if (evaluationResult.isError()) {
                handleErrorEvaluationResult(predicate, matchingCtx);
            } else {
                updateCandidatesInMatchingContext(predicate, evaluationResult.getBoolean(), matchingCtx, dataContainer);
            }
        }

        public CanonicalIndexMatchingContext build() {
            return matchingCtx;
        }

        public Builder combine(CanonicalIndexMatchingContext otherContext) {
            matchingCtx.addSatisfiedCandidates(otherContext.getMatchingCandidatesMask());
            return this;
        }
    }

}
