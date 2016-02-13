/*
 * This file is part of "hybris integration" plugin for Intellij IDEA.
 * Copyright (C) 2014-2015 Alexander Bartash <AlexanderBartash@gmail.com>
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

package com.intellij.idea.plugin.hybris.project.configurators.impl;

import com.intellij.idea.plugin.hybris.project.configurators.GroupModuleConfigurator;
import com.intellij.idea.plugin.hybris.project.settings.ConfigHybrisModuleDescriptor;
import com.intellij.idea.plugin.hybris.project.settings.DefaultHybrisModuleDescriptor;
import com.intellij.idea.plugin.hybris.project.settings.HybrisModuleDescriptor;
import com.intellij.idea.plugin.hybris.project.settings.PlatformHybrisModuleDescriptor;
import com.intellij.idea.plugin.hybris.settings.HybrisApplicationSettings;
import com.intellij.idea.plugin.hybris.settings.HybrisApplicationSettingsComponent;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by Martin Zdarsky (martin.zdarsky@hybris.com) on 24/08/15.
 */
public class DefaultGroupModuleConfigurator implements GroupModuleConfigurator {

    private Set<HybrisModuleDescriptor> requiredHybrisModuleDescriptorList;
    private boolean groupModules;
    private String[] groupCustom;
    private String[] groupOtherCustom;
    private String[] groupHybris;
    private String[] groupOtherHybris;

    @Override
    public void findDependencyModules(@NotNull final List<HybrisModuleDescriptor> modulesChosenForImport) {
        readSettings();
        if (!groupModules) {
            return;
        }
        requiredHybrisModuleDescriptorList = new HashSet<HybrisModuleDescriptor>();
        for (HybrisModuleDescriptor hybrisModuleDescriptor: modulesChosenForImport) {
            if (hybrisModuleDescriptor.isPreselected()) {
                requiredHybrisModuleDescriptorList.add(hybrisModuleDescriptor);
                requiredHybrisModuleDescriptorList.addAll(hybrisModuleDescriptor.getDependenciesPlainList());
            }
        }
    }

    @Override
    public void configure(@NotNull final ModifiableModuleModel modifiableModuleModel,
                          @NotNull final Module module,
                          @NotNull final HybrisModuleDescriptor moduleDescriptor) {
        if (!groupModules) {
            return;
        }
        String[] groupNamePath = getGroupName(moduleDescriptor);
        modifiableModuleModel.setModuleGroupPath(module, groupNamePath);
    }

    @Nullable
    protected String[] getGroupName(@NotNull final HybrisModuleDescriptor moduleDescriptor) {
        if (moduleDescriptor instanceof PlatformHybrisModuleDescriptor) {
            return groupHybris;
        }
        if (moduleDescriptor instanceof ConfigHybrisModuleDescriptor) {
            return groupCustom;
        }
        DefaultHybrisModuleDescriptor defaultHybrisModuleDescriptor = (DefaultHybrisModuleDescriptor) moduleDescriptor;
        if (defaultHybrisModuleDescriptor.isPreselected()) {
            return groupCustom;
        }
        if (defaultHybrisModuleDescriptor.isInCustomDir()) {
            return groupOtherCustom;
        }
        if (!requiredHybrisModuleDescriptorList.contains(moduleDescriptor)) {
            return groupOtherHybris;
        }
        return groupHybris;
    }

    private void readSettings() {
        final HybrisApplicationSettings hybrisApplicationSettings = HybrisApplicationSettingsComponent.getInstance().getState();
        groupModules = hybrisApplicationSettings.isGroupModules();
        groupCustom = toIdeaGroup(hybrisApplicationSettings.getGroupCustom());
        groupOtherCustom = toIdeaGroup(hybrisApplicationSettings.getGroupOtherCustom());
        groupHybris = toIdeaGroup(hybrisApplicationSettings.getGroupHybris());
        groupOtherHybris = toIdeaGroup(hybrisApplicationSettings.getGroupOtherHybris());
    }

    private String[] toIdeaGroup(final String group) {
        if (group == null || group.trim().isEmpty()) {
            return null;
        }
        return StringUtils.split(group, " ,.;>/\\");
    }
}
