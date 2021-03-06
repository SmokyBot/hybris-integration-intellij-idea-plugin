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

package com.intellij.idea.plugin.hybris.project.descriptors;

import com.google.common.collect.Iterables;
import com.intellij.idea.plugin.hybris.common.HybrisConstants;
import com.intellij.idea.plugin.hybris.project.exceptions.HybrisConfigurationException;
import com.intellij.idea.plugin.hybris.project.services.HybrisProjectService;
import com.intellij.idea.plugin.hybris.project.settings.jaxb.localextensions.ExtensionType;
import com.intellij.idea.plugin.hybris.project.settings.jaxb.localextensions.Hybrisconfig;
import com.intellij.idea.plugin.hybris.project.tasks.TaskProgressProcessor;
import com.intellij.idea.plugin.hybris.project.utils.FindHybrisModuleDescriptorByName;
import com.intellij.idea.plugin.hybris.settings.HybrisApplicationSettings;
import com.intellij.idea.plugin.hybris.settings.HybrisApplicationSettingsComponent;
import com.intellij.idea.plugin.hybris.settings.HybrisProjectSettingsComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import gnu.trove.THashSet;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.concurrent.GuardedBy;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static com.intellij.idea.plugin.hybris.common.utils.CollectionUtils.emptyIfNull;
import static com.intellij.util.containers.ContainerUtil.newArrayList;
import static com.intellij.util.containers.ContainerUtil.newHashSet;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.io.FilenameUtils.separatorsToSystem;

/**
 * Created 3:55 PM 13 June 2015.
 *
 * @author Alexander Bartash <AlexanderBartash@gmail.com>
 */
public class DefaultHybrisProjectDescriptor implements HybrisProjectDescriptor {

    private static final Logger LOG = Logger.getInstance(DefaultHybrisProjectDescriptor.class);
    @NotNull
    protected final List<HybrisModuleDescriptor> foundModules = newArrayList();
    @NotNull
    protected final List<HybrisModuleDescriptor> modulesChosenForImport = newArrayList();
    @NotNull
    @GuardedBy("lock")
    protected final Set<HybrisModuleDescriptor> alreadyOpenedModules = newHashSet();
    protected final Lock lock = new ReentrantLock();
    @NotNull
    protected final List<String> explicitlyDefinedModules = newArrayList();
    @Nullable
    protected Project project;
    @Nullable
    protected File rootDirectory;
    @Nullable
    protected File modulesFilesDirectory;
    @Nullable
    protected File sourceCodeFile;
    protected boolean openProjectSettingsAfterImport;
    protected Boolean importOotbModulesInReadOnlyMode;
    @Nullable
    protected File hybrisDistributionDirectory;
    @Nullable
    protected File externalExtensionsDirectory;
    @Nullable
    protected File externalConfigDirectory;
    @Nullable
    protected File externalDbDriversDirectory;
    @Nullable
    protected String javadocUrl;
    protected boolean createBackwardCyclicDependenciesForAddOns;
    @NotNull
    private ConfigHybrisModuleDescriptor configHybrisModuleDescriptor;
    @NotNull
    private PlatformHybrisModuleDescriptor platformHybrisModuleDescriptor;

    private void processLocalExtensions() {
        final ConfigHybrisModuleDescriptor configHybrisModuleDescriptor = findConfigDir();
        if (configHybrisModuleDescriptor == null) {
            return;
        }
        final Hybrisconfig hybrisconfig = unmarshalLocalExtensions(configHybrisModuleDescriptor);
        if (hybrisconfig == null) {
            return;
        }
        processHybrisConfig(hybrisconfig);
        preselectModules(configHybrisModuleDescriptor);
    }

    private void preselectModules(@NotNull final ConfigHybrisModuleDescriptor configHybrisModuleDescriptor) {
        Validate.notNull(configHybrisModuleDescriptor);
        for (HybrisModuleDescriptor hybrisModuleDescriptor : foundModules) {
            if (explicitlyDefinedModules.contains(StringUtils.lowerCase(hybrisModuleDescriptor.getName()))) {
                hybrisModuleDescriptor.setInLocalExtensions(true);
            }
            if (hybrisModuleDescriptor instanceof PlatformHybrisModuleDescriptor) {
                PlatformHybrisModuleDescriptor platformDescriptor = (PlatformHybrisModuleDescriptor) hybrisModuleDescriptor;
                Set<HybrisModuleDescriptor> dependenciesTree = newHashSet(platformDescriptor.getDependenciesTree());
                dependenciesTree.add(configHybrisModuleDescriptor);
                platformDescriptor.setDependenciesTree(dependenciesTree);
            }
        }
        configHybrisModuleDescriptor.setImportStatus(HybrisModuleDescriptor.IMPORT_STATUS.MANDATORY);
        configHybrisModuleDescriptor.setPreselected(true);
    }

    @Nullable
    private ConfigHybrisModuleDescriptor findConfigDir() {
        final List<ConfigHybrisModuleDescriptor> foundConfigModules = newArrayList();
        PlatformHybrisModuleDescriptor platformHybrisModuleDescriptor = null;
        for (HybrisModuleDescriptor moduleDescriptor : foundModules) {
            if (moduleDescriptor instanceof ConfigHybrisModuleDescriptor) {
                foundConfigModules.add((ConfigHybrisModuleDescriptor) moduleDescriptor);
            }
            if (moduleDescriptor instanceof PlatformHybrisModuleDescriptor) {
                platformHybrisModuleDescriptor = (PlatformHybrisModuleDescriptor) moduleDescriptor;
            }
        }
        if (platformHybrisModuleDescriptor == null) {
            if (foundConfigModules.size() == 1) {
                return foundConfigModules.get(0);
            }
            return null;
        }
        final File configDir;
        if (externalConfigDirectory != null) {
            configDir = externalConfigDirectory;
            if (!configDir.isDirectory()) {
                return null;
            }
        } else {
            configDir = getExpectedConfigDir(platformHybrisModuleDescriptor);
            if (configDir == null || !configDir.isDirectory()) {
                if (foundConfigModules.size() == 1) {
                    return foundConfigModules.get(0);
                }
                return null;
            }
        }
        for (ConfigHybrisModuleDescriptor configHybrisModuleDescriptor : foundConfigModules) {
            if (FileUtil.filesEqual(configHybrisModuleDescriptor.getRootDirectory(), configDir)) {
                return configHybrisModuleDescriptor;
            }
        }
        final HybrisProjectService hybrisProjectService = ServiceManager.getService(HybrisProjectService.class);

        if (hybrisProjectService.isConfigModule(configDir)) {
            try {
                final ConfigHybrisModuleDescriptor configHybrisModuleDescriptor = new ConfigHybrisModuleDescriptor(
                    configDir,
                    platformHybrisModuleDescriptor.getRootProjectDescriptor()
                );
                LOG.info("Creating Overriden Config module in local.properties for " + configDir.getAbsolutePath());
                foundModules.add(configHybrisModuleDescriptor);
                Collections.sort(foundModules);
                return configHybrisModuleDescriptor;
            } catch (HybrisConfigurationException e) {
                // no-op
            }
        }
        return null;
    }

    private File getExpectedConfigDir(final PlatformHybrisModuleDescriptor platformHybrisModuleDescriptor) {
        final File platformDir = platformHybrisModuleDescriptor.getRootDirectory();
        final File expectedConfigDir = new File(platformDir + HybrisConstants.CONFIG_RELATIVE_PATH);
        if (!expectedConfigDir.isDirectory()) {
            return null;
        }
        final File propertiesFile = new File(expectedConfigDir, HybrisConstants.LOCAL_PROPERTIES);
        if (!propertiesFile.exists()) {
            return expectedConfigDir;
        }

        final Properties properties = new Properties();
        try (FileReader fr = new FileReader(propertiesFile)) {
            properties.load(fr);
        } catch (IOException e) {
            return expectedConfigDir;
        }

        String hybrisConfig = (String) properties.get(HybrisConstants.HYBRIS_CONFIG_DIR_KEY);
        if (hybrisConfig == null) {
            return expectedConfigDir;
        }

        hybrisConfig = hybrisConfig.replace(
            HybrisConstants.PLATFORM_HOME_PLACEHOLDER,
            platformHybrisModuleDescriptor.getRootDirectory().getPath()
        );
        hybrisConfig = separatorsToSystem(hybrisConfig);

        final File hybrisConfigDir = new File(hybrisConfig);
        if (hybrisConfigDir.isDirectory()) {
            return hybrisConfigDir;
        }

        return expectedConfigDir;
    }

    private void processHybrisConfig(@NotNull final Hybrisconfig hybrisconfig) {
        final List<ExtensionType> extensionTypeList = hybrisconfig.getExtensions().getExtension();
        for (ExtensionType extensionType : extensionTypeList) {
            final String name = StringUtils.lowerCase(extensionType.getName());
            if (name != null) {
                explicitlyDefinedModules.add(name);
                continue;
            }
            final String dir = extensionType.getDir();
            final int indexSlash = dir.lastIndexOf('/');
            final int indexBack = dir.lastIndexOf('\\');
            final int index = Math.max(indexSlash, indexBack);
            if (index == -1) {
                explicitlyDefinedModules.add(StringUtils.lowerCase(dir));
            } else {
                explicitlyDefinedModules.add(StringUtils.lowerCase(dir.substring(index + 1)));
            }
        }
    }

    @Nullable
    private Hybrisconfig unmarshalLocalExtensions(@NotNull final ConfigHybrisModuleDescriptor configHybrisModuleDescriptor) {
        Validate.notNull(configHybrisModuleDescriptor);

        return unmarshalLocalExtensions(new File(
            configHybrisModuleDescriptor.getRootDirectory(),
            HybrisConstants.LOCAL_EXTENSIONS_XML
        ));
    }

    @Nullable
    public static Hybrisconfig unmarshalLocalExtensions(final File localextensions) {
        if (!localextensions.exists()) {
            return null;
        }
        try {
            final JAXBContext jaxbContext = JAXBContext.newInstance(Hybrisconfig.class);
            if (null == jaxbContext) {
                LOG.error(String.format(
                    "Can not unmarshal '%s' because JAXBContext has not been created.",
                    localextensions.getAbsolutePath()
                ));

                return null;
            }

            final Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();

            return (Hybrisconfig) jaxbUnmarshaller.unmarshal(localextensions);
        } catch (JAXBException e) {
            LOG.error("Can not unmarshal " + localextensions.getAbsolutePath(), e);
        }

        return null;
    }

    @Override
    public void setProject(@Nullable final Project project) {
        if (project == null) {
            return;
        }
        // the project may not be hybris based project.
        final HybrisProjectSettingsComponent hybrisProjectSettings = HybrisProjectSettingsComponent.getInstance(project);
        if (hybrisProjectSettings == null) {
            return;
        }
        if (hybrisProjectSettings.getState() != null) {
            if (hybrisProjectSettings.getState().isHybrisProject()) {
                setHybrisProject(project);
            }
        }
    }

    protected void scanDirectoryForHybrisModules(
        @NotNull final File rootDirectory,
        @Nullable final TaskProgressProcessor<File> progressListenerProcessor,
        @Nullable final TaskProgressProcessor<List<File>> errorsProcessor
    ) throws InterruptedException, IOException {
        Validate.notNull(rootDirectory);

        this.foundModules.clear();

        LOG.info("Scanning for modules");
        final List<File> moduleRootDirectories = this.findModuleRoots(rootDirectory, progressListenerProcessor);
        if (externalExtensionsDirectory != null) {
            LOG.info("Scanning for external modules");
            moduleRootDirectories.addAll(
                this.findModuleRoots(externalExtensionsDirectory, progressListenerProcessor)
            );
        }

        final List<HybrisModuleDescriptor> moduleDescriptors = newArrayList();
        final List<File> pathsFailedToImport = newArrayList();

        addRootModule(rootDirectory, moduleDescriptors, pathsFailedToImport);

        final HybrisModuleDescriptorFactory hybrisModuleDescriptorFactory = ServiceManager.getService(
            HybrisModuleDescriptorFactory.class
        );

        for (File moduleRootDirectory : moduleRootDirectories) {
            try {
                final HybrisModuleDescriptor moduleDescriptor = hybrisModuleDescriptorFactory.createDescriptor(
                    moduleRootDirectory, this
                );
                moduleDescriptors.add(moduleDescriptor);
            } catch (HybrisConfigurationException e) {
                LOG.error("Can not import a module using path: " + pathsFailedToImport, e);

                pathsFailedToImport.add(moduleRootDirectory);
            }
        }

        if (null != errorsProcessor) {
            if (errorsProcessor.shouldContinue(pathsFailedToImport)) {
                throw new InterruptedException("Modules scanning has been interrupted.");
            }
        }

        Collections.sort(moduleDescriptors);

        this.buildDependencies(moduleDescriptors);

        this.foundModules.addAll(moduleDescriptors);
    }

    private void addRootModule(
        final File rootDirectory, final List<HybrisModuleDescriptor> moduleDescriptors,
        final List<File> pathsFailedToImport
    ) {
        final HybrisApplicationSettings hybrisApplicationSettings = HybrisApplicationSettingsComponent.getInstance()
                                                                                                      .getState();
        final boolean groupModules = hybrisApplicationSettings.isGroupModules();
        if (groupModules) {
            return;
        }
        try {
            moduleDescriptors.add(new RootModuleDescriptor(rootDirectory, this));
        } catch (HybrisConfigurationException e) {
            LOG.error("Can not import a module using path: " + pathsFailedToImport, e);
            pathsFailedToImport.add(rootDirectory);
        }
    }

    @Override
    public void setHybrisProject(@Nullable final Project project) {
        this.project = project;
    }

    private List<File> findModuleRoots(
        @NotNull final File rootProjectDirectory,
        @Nullable final TaskProgressProcessor<File> progressListenerProcessor
    ) throws InterruptedException, IOException {
        Validate.notNull(rootProjectDirectory);
        List<File> paths = newArrayList();
        if (null != progressListenerProcessor) {
            if (!progressListenerProcessor.shouldContinue(rootProjectDirectory)) {
                LOG.error("Modules scanning has been interrupted.");
                throw new InterruptedException("Modules scanning has been interrupted.");
            }
        }

        final HybrisProjectService hybrisProjectService = ServiceManager.getService(HybrisProjectService.class);

        if (hybrisProjectService.isHybrisModule(rootProjectDirectory)) {
            LOG.info("Detected hybris module " + rootProjectDirectory.getAbsolutePath());
            paths.add(rootProjectDirectory);
            return paths;
        }
        if (hybrisProjectService.isConfigModule(rootProjectDirectory)) {
            LOG.info("Detected config module " + rootProjectDirectory.getAbsolutePath());
            paths.add(rootProjectDirectory);
            return paths;
        }

        if (hybrisProjectService.isGradleModule(rootProjectDirectory) && !FileUtil.filesEqual(
            rootProjectDirectory,
            rootDirectory
        )) {
            LOG.info("Detected gradle module " + rootProjectDirectory.getAbsolutePath());
            paths.add(rootProjectDirectory);
            return paths;
        }

        if (hybrisProjectService.isMavenModule(rootProjectDirectory) && !FileUtil.filesEqual(
            rootProjectDirectory,
            rootDirectory
        )) {
            LOG.info("Detected maven module " + rootProjectDirectory.getAbsolutePath());
            paths.add(rootProjectDirectory);
            return paths;
        }

        if (hybrisProjectService.isPlatformModule(rootProjectDirectory)) {
            LOG.info("Detected platform module " + rootProjectDirectory.getAbsolutePath());
            paths.add(rootProjectDirectory);
        } else if (hybrisProjectService.isEclipseModule(rootProjectDirectory) && !FileUtil.filesEqual(
            rootProjectDirectory,
            rootDirectory
        )) {
            LOG.info("Detected eclipse module " + rootProjectDirectory.getAbsolutePath());
            paths.add(rootProjectDirectory);
            return paths;
        }

        scanSubrirectories(paths, rootProjectDirectory.toPath(), progressListenerProcessor);

        return paths;
    }

    private void scanSubrirectories(
        @NotNull final List<File> paths,
        @NotNull final Path rootProjectDirectory,
        @Nullable final TaskProgressProcessor<File> progressListenerProcessor
    ) throws InterruptedException, IOException {
        if (!Files.isDirectory(rootProjectDirectory)) {
            return;
        }
        final HybrisApplicationSettings hybrisApplicationSettings = HybrisApplicationSettingsComponent.getInstance()
                                                                                                      .getState();
        final boolean followSymlink = hybrisApplicationSettings.isFollowSymlink();
        final DirectoryStream<Path> files = Files.newDirectoryStream(rootProjectDirectory, file -> {
            if (file == null) {
                return false;
            }
            if (!Files.isDirectory(file)) {
                return false;
            }
            if (isDirectoryExcluded(file)) {
                return false;
            }
            if (Files.isSymbolicLink(file) && !followSymlink) {
                return false;
            }
            return true;
        });
        if (files != null) {
            for (Path file : files) {
                paths.addAll(findModuleRoots(file.toFile(), progressListenerProcessor));
            }
            files.close();
        }
    }

    private boolean isDirectoryExcluded(final Path file) {
        if (file.toString().endsWith(HybrisConstants.EXCLUDE_BOOTSTRAP_DIRECTORY) ||
            file.toString().endsWith(HybrisConstants.EXCLUDE_DATA_DIRECTORY) ||
            file.toString().endsWith(HybrisConstants.EXCLUDE_ECLIPSEBIN_DIRECTORY) ||
            file.toString().endsWith(HybrisConstants.EXCLUDE_GIT_DIRECTORY) ||
            file.toString().endsWith(HybrisConstants.EXCLUDE_IDEA_DIRECTORY) ||
            file.toString().endsWith(HybrisConstants.EXCLUDE_MACOSX_DIRECTORY) ||
            file.toString().endsWith(HybrisConstants.EXCLUDE_IDEA_MODULE_FILES_DIRECTORY) ||
            file.toString().endsWith(HybrisConstants.EXCLUDE_LIB_DIRECTORY) ||
            file.toString().endsWith(HybrisConstants.EXCLUDE_LOG_DIRECTORY) ||
            file.toString().endsWith(HybrisConstants.EXCLUDE_RESOURCES_DIRECTORY) ||
            file.toString().endsWith(HybrisConstants.EXCLUDE_SVN_DIRECTORY) ||
            file.toString().endsWith(HybrisConstants.EXCLUDE_TEMP_DIRECTORY) ||
            file.toString().endsWith(HybrisConstants.EXCLUDE_TOMCAT_DIRECTORY) ||
            file.toString().endsWith(HybrisConstants.EXCLUDE_TMP_DIRECTORY)) {
            return true;
        }
        if (file.toString().contains(HybrisConstants.EXCLUDE_ANT_DIRECTORY)) {
            return true;
        }
        return false;
    }

    protected void buildDependencies(@NotNull final Iterable<HybrisModuleDescriptor> moduleDescriptors) {
        Validate.notNull(moduleDescriptors);

        for (HybrisModuleDescriptor moduleDescriptor : moduleDescriptors) {

            final Set<String> requiredExtensionNames = moduleDescriptor.getRequiredExtensionNames();

            if (isEmpty(requiredExtensionNames)) {
                continue;
            }

            final Set<HybrisModuleDescriptor> dependencies = new HashSet<HybrisModuleDescriptor>(
                requiredExtensionNames.size()
            );

            for (String requiresExtensionName : requiredExtensionNames) {

                final Iterable<HybrisModuleDescriptor> dependsOn = this.findHybrisModuleDescriptorsByName(
                    moduleDescriptors, requiresExtensionName
                );

                if (Iterables.isEmpty(dependsOn)) {
                    LOG.warn(String.format(
                        "Module '%s' contains unsatisfied dependency '%s'.",
                        moduleDescriptor.getName(), requiresExtensionName
                    ));
                } else {
                    for (HybrisModuleDescriptor hybrisModuleDescriptor : dependsOn) {
                        dependencies.add(hybrisModuleDescriptor);
                    }
                }
            }

            if (moduleDescriptor.isAddOn()) {
                this.processAddOnBackwardDependencies(moduleDescriptors, moduleDescriptor, dependencies);
            }

            moduleDescriptor.setDependenciesTree(dependencies);
        }
    }

    @Nullable
    @Override
    public Project getProject() {
        return this.project;
    }

    protected void processAddOnBackwardDependencies(
        @NotNull final Iterable<HybrisModuleDescriptor> moduleDescriptors,
        @NotNull final HybrisModuleDescriptor addOn,
        @NotNull final Set<HybrisModuleDescriptor> addOnDependencies
    ) {
        Validate.notNull(moduleDescriptors);
        Validate.notNull(addOn);
        Validate.notNull(addOnDependencies);

        if (isCreateBackwardCyclicDependenciesForAddOn()) {
            for (HybrisModuleDescriptor moduleDescriptor : moduleDescriptors) {
                if (moduleDescriptor.getRequiredExtensionNames().contains(addOn.getName())) {
                    addOnDependencies.add(moduleDescriptor);
                }
            }
        }
    }

    @NotNull
    protected Iterable<HybrisModuleDescriptor> findHybrisModuleDescriptorsByName(
        @NotNull final Iterable<HybrisModuleDescriptor> moduleDescriptors,
        @NotNull final String requiresExtensionName
    ) {
        Validate.notNull(moduleDescriptors);
        Validate.notEmpty(requiresExtensionName);

        return emptyIfNull(Iterables.filter(
            moduleDescriptors, new FindHybrisModuleDescriptorByName(requiresExtensionName)
        ));
    }

    @NotNull
    @Override
    public List<HybrisModuleDescriptor> getFoundModules() {
        return Collections.unmodifiableList(this.foundModules);
    }

    @NotNull
    @Override
    public List<HybrisModuleDescriptor> getModulesChosenForImport() {
        return this.modulesChosenForImport;
    }

    @Override
    public void setModulesChosenForImport(@NotNull final List<HybrisModuleDescriptor> moduleDescriptors) {
        Validate.notNull(moduleDescriptors);

        this.modulesChosenForImport.clear();
        this.modulesChosenForImport.addAll(moduleDescriptors);
        moduleDescriptors.stream().forEach(module -> {
            if (module instanceof ConfigHybrisModuleDescriptor) {
                configHybrisModuleDescriptor = (ConfigHybrisModuleDescriptor) module;
            }
            if (module instanceof PlatformHybrisModuleDescriptor) {
                platformHybrisModuleDescriptor = (PlatformHybrisModuleDescriptor) module;
            }
        });
    }

    @NotNull
    @Override
    public ConfigHybrisModuleDescriptor getConfigHybrisModuleDescriptor() {
        return configHybrisModuleDescriptor;
    }

    @NotNull
    @Override
    public PlatformHybrisModuleDescriptor getPlatformHybrisModuleDescriptor() {
        return platformHybrisModuleDescriptor;
    }

    @NotNull
    @Override
    public Set<HybrisModuleDescriptor> getAlreadyOpenedModules() {
        if (null == getProject()) {
            return Collections.emptySet();
        }

        this.lock.lock();

        try {
            if (this.alreadyOpenedModules.isEmpty()) {
                this.alreadyOpenedModules.addAll(this.getAlreadyOpenedModules(getProject()));
            }
        } finally {
            this.lock.unlock();
        }

        return Collections.unmodifiableSet(this.alreadyOpenedModules);
    }

    @Nullable
    @Override
    public File getRootDirectory() {
        return this.rootDirectory;
    }

    @Override
    public void clear() {
        this.rootDirectory = null;
        this.hybrisDistributionDirectory = null;
        this.externalExtensionsDirectory = null;
        this.externalConfigDirectory = null;
        this.externalDbDriversDirectory = null;
        this.foundModules.clear();
        this.modulesChosenForImport.clear();
        this.explicitlyDefinedModules.clear();
    }

    @Nullable
    @Override
    public File getModulesFilesDirectory() {
        return this.modulesFilesDirectory;
    }

    @Override
    public void setModulesFilesDirectory(@Nullable final File modulesFilesDirectory) {
        this.modulesFilesDirectory = modulesFilesDirectory;
    }

    @Nullable
    @Override
    public File getSourceCodeFile() {
        return sourceCodeFile;
    }

    @Override
    public void setSourceCodeFile(@Nullable final File sourceCodeFile) {
        this.sourceCodeFile = sourceCodeFile;
    }

    @Override
    public void setRootDirectoryAndScanForModules(
        @NotNull final File rootDirectory,
        @Nullable final TaskProgressProcessor<File> progressListenerProcessor,
        @Nullable final TaskProgressProcessor<List<File>> errorsProcessor
    ) {
        Validate.notNull(rootDirectory);

        this.rootDirectory = rootDirectory;

        try {
            this.scanDirectoryForHybrisModules(rootDirectory, progressListenerProcessor, errorsProcessor);
            this.processLocalExtensions();
        } catch (InterruptedException | IOException e) {
            LOG.warn(e);

            this.rootDirectory = null;
            this.foundModules.clear();
            this.explicitlyDefinedModules.clear();
        }
    }

    @Override
    public boolean isOpenProjectSettingsAfterImport() {
        return this.openProjectSettingsAfterImport;
    }

    @Override
    public void setOpenProjectSettingsAfterImport(final boolean openProjectSettingsAfterImport) {
        this.openProjectSettingsAfterImport = openProjectSettingsAfterImport;
    }

    @Override
    public Boolean isImportOotbModulesInReadOnlyMode() {
        return importOotbModulesInReadOnlyMode;
    }

    @Override
    public void setImportOotbModulesInReadOnlyMode(final boolean importOotbModulesInReadOnlyMode) {
        this.importOotbModulesInReadOnlyMode = importOotbModulesInReadOnlyMode;
    }

    @Override
    @Nullable
    public File getHybrisDistributionDirectory() {
        return hybrisDistributionDirectory;
    }

    @Override
    public void setHybrisDistributionDirectory(@Nullable final File hybrisDistributionDirectory) {
        this.hybrisDistributionDirectory = hybrisDistributionDirectory;
    }

    @Override
    @Nullable
    public File getExternalExtensionsDirectory() {
        return externalExtensionsDirectory;
    }

    @Override
    public void setExternalExtensionsDirectory(@Nullable final File externalExtensionsDirectory) {
        this.externalExtensionsDirectory = externalExtensionsDirectory;
    }

    @Override
    @Nullable
    public File getExternalConfigDirectory() {
        return externalConfigDirectory;
    }

    @Override
    public void setExternalConfigDirectory(@Nullable final File externalConfigDirectory) {
        this.externalConfigDirectory = externalConfigDirectory;
    }

    @Override
    @Nullable
    public File getExternalDbDriversDirectory() {
        return externalDbDriversDirectory;
    }

    @Override
    public void setExternalDbDriversDirectory(@Nullable final File externalDbDriversDirectory) {
        this.externalDbDriversDirectory = externalDbDriversDirectory;
    }

    @Nullable
    @Override
    public String getJavadocUrl() {
        return javadocUrl;
    }

    @Override
    public void setJavadocUrl(@Nullable final String javadocUrl) {
        this.javadocUrl = javadocUrl;
    }

    @Override
    public void setCreateBackwardCyclicDependenciesForAddOns(final boolean createBackwardCyclicDependenciesForAddOns) {
        this.createBackwardCyclicDependenciesForAddOns = createBackwardCyclicDependenciesForAddOns;
    }

    @Override
    public boolean isCreateBackwardCyclicDependenciesForAddOn() {
        return createBackwardCyclicDependenciesForAddOns;
    }

    @NotNull
    protected Set<HybrisModuleDescriptor> getAlreadyOpenedModules(@NotNull final Project project) {
        Validate.notNull(project);

        final HybrisModuleDescriptorFactory hybrisModuleDescriptorFactory = ServiceManager.getService(
            HybrisModuleDescriptorFactory.class
        );

        final Set<HybrisModuleDescriptor> existingModules = new THashSet<HybrisModuleDescriptor>();

        for (Module module : ModuleManager.getInstance(project).getModules()) {
            try {
                final VirtualFile[] contentRoots = ModuleRootManager.getInstance(module).getContentRoots();

                if (!ArrayUtils.isEmpty(contentRoots)) {
                    existingModules.add(hybrisModuleDescriptorFactory.createDescriptor(
                        VfsUtil.virtualToIoFile(contentRoots[0]), this
                    ));
                }
            } catch (HybrisConfigurationException e) {
                LOG.error(e);
            }
        }

        return existingModules;
    }
}
