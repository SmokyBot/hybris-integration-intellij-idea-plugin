/*
 * This file is part of "hybris integration" plugin for Intellij IDEA.
 * Copyright (C) 2014-2016 Alexander Bartash <AlexanderBartash@gmail.com>
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

package com.intellij.idea.plugin.hybris.settings;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.util.Set;

import static com.intellij.util.containers.ContainerUtil.newHashSet;

/**
 * Created 6:51 PM 28 June 2015.
 *
 * @author Alexander Bartash <AlexanderBartash@gmail.com>
 */
public class HybrisProjectSettings {

    protected boolean hybrisProject;
    protected String customDirectory;
    protected String hybrisDirectory;
    protected String importedByVersion;
    protected String sourceCodeFile;
    protected String externalExtensionsDirectory;
    protected String externalConfigDirectory;
    protected String externalDbDriversDirectory;
    protected String ideModulesFilesDirectory;
    protected boolean importOotbModulesInReadOnlyMode;
    protected boolean createBackwardCyclicDependenciesForAddOns = false;
    protected Set<String> completeSetOfAvailableExtensionsInHybris = newHashSet();
    protected Set<String> unusedExtensions = newHashSet();

    public boolean isHybrisProject() {
        return hybrisProject;
    }

    public void setHybrisProject(final boolean hybrisProject) {
        this.hybrisProject = hybrisProject;
    }

    public String getCustomDirectory() {
        return customDirectory;
    }

    public void setCustomDirectory(final String customDirectory) {
        this.customDirectory = customDirectory;
    }

    public String getHybrisDirectory() {
        return hybrisDirectory;
    }

    public void setHybrisDirectory(final String hybrisDirectory) {
        this.hybrisDirectory = hybrisDirectory;
    }

    public String getImportedByVersion() {
        return importedByVersion;
    }

    public void setImportedByVersion(final String importedByVersion) {
        this.importedByVersion = importedByVersion;
    }

    public String getSourceCodeFile() {
        return sourceCodeFile;
    }

    public void setSourceCodeFile(final String sourceCodeFile) {
        this.sourceCodeFile = sourceCodeFile;
    }

    public String getExternalExtensionsDirectory() {
        return externalExtensionsDirectory;
    }

    public void setExternalExtensionsDirectory(final String externalExtensionsDirectory) {
        this.externalExtensionsDirectory = externalExtensionsDirectory;
    }

    public String getExternalConfigDirectory() {
        return externalConfigDirectory;
    }

    public void setExternalConfigDirectory(final String externalConfigDirectory) {
        this.externalConfigDirectory = externalConfigDirectory;
    }

    public String getExternalDbDriversDirectory() {
        return externalDbDriversDirectory;
    }

    public void setExternalDbDriversDirectory(final String externalDbDriversDirectory) {
        this.externalDbDriversDirectory = externalDbDriversDirectory;
    }

    public String getIdeModulesFilesDirectory() {
        return ideModulesFilesDirectory;
    }

    public void setIdeModulesFilesDirectory(final String ideModulesFilesDirectory) {
        this.ideModulesFilesDirectory = ideModulesFilesDirectory;
    }

    public boolean getImportOotbModulesInReadOnlyMode() {
        return importOotbModulesInReadOnlyMode;
    }

    public void setImportOotbModulesInReadOnlyMode(final boolean importOotbModulesInReadOnlyMode) {
        this.importOotbModulesInReadOnlyMode = importOotbModulesInReadOnlyMode;
    }

    public Set<String> getCompleteSetOfAvailableExtensionsInHybris() {
        return completeSetOfAvailableExtensionsInHybris;
    }

    public void setCompleteSetOfAvailableExtensionsInHybris(final Set<String> completeSetOfAvailableExtensionsInHybris) {
        this.completeSetOfAvailableExtensionsInHybris = completeSetOfAvailableExtensionsInHybris;
    }

    public boolean isCreateBackwardCyclicDependenciesForAddOns() {
        return createBackwardCyclicDependenciesForAddOns;
    }

    public void setCreateBackwardCyclicDependenciesForAddOns(final boolean createBackwardCyclicDependenciesForAddOns) {
        this.createBackwardCyclicDependenciesForAddOns = createBackwardCyclicDependenciesForAddOns;
    }

    public Set<String> getUnusedExtensions() {
        return unusedExtensions;
    }

    public void setUnusedExtensions(final Set<String> unusedExtensions) {
        this.unusedExtensions = unusedExtensions;
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
            .append(hybrisProject)
            .append(customDirectory)
            .append(hybrisDirectory)
            .append(importedByVersion)
            .append(sourceCodeFile)
            .append(externalExtensionsDirectory)
            .append(externalConfigDirectory)
            .append(externalDbDriversDirectory)
            .append(ideModulesFilesDirectory)
            .append(importOotbModulesInReadOnlyMode)
            .append(completeSetOfAvailableExtensionsInHybris)
            .append(createBackwardCyclicDependenciesForAddOns)
            .append(unusedExtensions)
            .toHashCode();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }

        if (null == o || getClass() != o.getClass()) {
            return false;
        }

        final HybrisProjectSettings other = (HybrisProjectSettings) o;

        return new EqualsBuilder()
            .append(hybrisProject, other.hybrisProject)
            .append(customDirectory, other.customDirectory)
            .append(hybrisDirectory, other.hybrisDirectory)
            .append(importedByVersion, other.importedByVersion)
            .append(sourceCodeFile, other.sourceCodeFile)
            .append(externalExtensionsDirectory, other.externalExtensionsDirectory)
            .append(externalConfigDirectory, other.externalConfigDirectory)
            .append(externalDbDriversDirectory, other.externalDbDriversDirectory)
            .append(ideModulesFilesDirectory, other.ideModulesFilesDirectory)
            .append(importOotbModulesInReadOnlyMode, other.importOotbModulesInReadOnlyMode)
            .append(completeSetOfAvailableExtensionsInHybris, other.completeSetOfAvailableExtensionsInHybris)
            .append(createBackwardCyclicDependenciesForAddOns, other.createBackwardCyclicDependenciesForAddOns)
            .append(unusedExtensions, other.unusedExtensions)
            .isEquals();
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("HybrisProjectSettings{");
        sb.append("hybrisProject=").append(hybrisProject);
        sb.append("customDirectoryPath=").append(customDirectory);
        sb.append("hybrisDirectory=").append(hybrisDirectory);
        sb.append("importedByVersion=").append(importedByVersion);
        sb.append("sourceCodeFile=").append(sourceCodeFile);
        sb.append("externalExtensionsDirectory=").append(externalExtensionsDirectory);
        sb.append("externalConfigDirectory=").append(externalConfigDirectory);
        sb.append("externalDbDriversDirectory=").append(externalDbDriversDirectory);
        sb.append("ideModulesFilesDirectory=").append(ideModulesFilesDirectory);
        sb.append("importOotbModulesInReadOnlyMode=").append(importOotbModulesInReadOnlyMode);
        sb.append("completeSetOfAvailableExtensionsInHybris=").append(completeSetOfAvailableExtensionsInHybris);
        sb.append("createBackwardCyclicDependenciesForAddOns=").append(createBackwardCyclicDependenciesForAddOns);
        sb.append("unusedExtensions=").append(unusedExtensions);
        sb.append('}');
        return sb.toString();
    }
}
