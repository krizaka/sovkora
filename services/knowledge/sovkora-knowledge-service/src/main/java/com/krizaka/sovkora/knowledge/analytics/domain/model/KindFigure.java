package com.krizaka.sovkora.knowledge.analytics.domain.model;

/**
 * One kind's figures over the range.
 *
 * @param kind the kind
 * @param unit what its value measures
 * @param count events, with the previous period
 * @param subjects distinct subjects
 * @param valueSum the sum of the values, when the kind has a unit
 * @param valueMean the mean value, when the kind has a unit
 */
public record KindFigure(
    String kind, ValueUnit unit, Figure count, long subjects, Double valueSum, Double valueMean) {}
