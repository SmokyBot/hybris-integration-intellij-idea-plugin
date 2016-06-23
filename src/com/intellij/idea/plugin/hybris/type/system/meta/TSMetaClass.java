/*
 * This file is part of "hybris integration" plugin for Intellij IDEA.
 * Copyright (C) 2014-2016 Alexander Bartash <AlexanderBartash@gmail.com>
 * Copyright (C) 2016 Viatra2 s.r.o <viatra2@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.intellij.idea.plugin.hybris.type.system.meta;

import com.intellij.idea.plugin.hybris.type.system.model.ItemType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.stream.Stream;

/**
 * Created by Martin Zdarsky-Jones (martin.zdarsky@hybris.com) on 15/06/2016.
 */
public interface TSMetaClass {

    @NotNull
    String getName();

    @Nullable
    String getExtendedMetaClassName();

    @NotNull
    Iterable<? extends TSMetaProperty> getProperties();

    @NotNull
    Stream<? extends TSMetaProperty> getPropertiesStream();

    @NotNull
    Collection<? extends TSMetaProperty> findPropertiesByName(@NotNull String name);

    @NotNull
    Stream<? extends ItemType> getAllDomsStream();

}