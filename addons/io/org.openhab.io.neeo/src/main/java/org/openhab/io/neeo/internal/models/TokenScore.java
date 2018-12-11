/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.io.neeo.internal.models;

import java.util.Objects;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * The model represents a token score for a given item (as defined by tokensearch.js)
 *
 * @author Tim Roberts - Initial Contribution
 * @param <T> the type that will be scored
 */
@NonNullByDefault
public class TokenScore<T> implements Comparable<TokenScore<T>> {

    /** The score */
    private final double score;

    /** The item being scored */
    private final T item;

    /**
     * Creates a new token score
     *
     * @param score the score
     * @param item the non-null item
     */
    public TokenScore(double score, T item) {
        Objects.requireNonNull(item, "item cannot be null");

        this.score = score;
        this.item = item;
    }

    /**
     * Gets the score
     *
     * @return the score
     */
    public double getScore() {
        return score;
    }

    /**
     * Gets the item
     *
     * @return the item
     */
    public T getItem() {
        return item;
    }

    @Override
    public int compareTo(@Nullable TokenScore<T> o) {
        if (o == null) {
            return 1;
        }

        int i = Double.compare(o.score, score);

        if (i == 0) {
            if (o.item == null) {
                return 1;
            }
            i = o.item.toString().compareTo(item.toString());
        }
        return i;
    }

}
