/*******************************************************************************
 * Copyright (c) 2012, 2014 Torkild U. Resheim.
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Torkild U. Resheim - initial API and implementation
 *******************************************************************************/
package no.resheim.eclipse.utils.launcher.macosx.handlers;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import no.resheim.eclipse.utils.launcher.core.EclipseConfiguration;
import no.resheim.eclipse.utils.launcher.core.JRE;
import no.resheim.eclipse.utils.launcher.core.LaunchException;
import no.resheim.eclipse.utils.launcher.core.LauncherPlugin;
import no.resheim.eclipse.utils.launcher.macosx.LaunchOptionsDialog;
import no.resheim.eclipse.utils.launcher.macosx.LaunchOptionsDialog.DebugMode;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IContributionItem;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.internal.ide.ChooseWorkspaceData;
import org.eclipse.ui.menus.CommandContributionItem;
import org.eclipse.ui.menus.CommandContributionItemParameter;
import org.eclipse.ui.menus.ExtensionContributionFactory;
import org.eclipse.ui.menus.IContributionRoot;
import org.eclipse.ui.services.IServiceLocator;
import org.eclipse.ui.statushandlers.StatusManager;

/**
 * The menu for selecting the workspace to use when starting Eclipse. Lists all
 * recent workspaces and provides an "Advanced..." option for more detailed
 * configuration.
 *
 * @see LaunchOptionsDialog
 * @since 2.0
 */
@SuppressWarnings("restriction")
public class OpenWorkspaceMenu extends ExtensionContributionFactory {

	private static final String COMMAND_ID = "no.resheim.eclipse.launcherutil.commands.newInstance"; //$NON-NLS-1$

	public OpenWorkspaceMenu() {
	}

	private IContributionItem[] getContributionItems(IServiceLocator serviceLocator) {
		List<IContributionItem> list = new ArrayList<IContributionItem>();
		try {
			final ChooseWorkspaceData data = new ChooseWorkspaceData(Platform.getInstanceLocation().getURL());
			data.readPersistedData();
			String current = data.getInitialDefault();
			String[] workspaces = data.getRecentWorkspaces();
			for (int i = 0; i < workspaces.length; i++) {
				if (workspaces[i] != null && !workspaces[i].equals(current)) {
					list.add(createOpenCommand(serviceLocator, workspaces[i], workspaces[i]));
				}
			}
			if (!list.isEmpty()) {
				list.add(new Separator());
			}
			list.add(createOpenCommand(serviceLocator, Messages.OpenWorkspaceMenu_Other, null));
		} catch (Exception e) {
			IStatus newStatus = new Status(IStatus.ERROR, LauncherPlugin.PLUGIN_ID,
					"Could not create workspace chooser menu item.", e); //$NON-NLS-1$
			StatusManager.getManager().handle(newStatus);
		}
		return list.toArray(new IContributionItem[list.size()]);
	}

	@Override
	public void createContributionItems(IServiceLocator serviceLocator, IContributionRoot additions) {
		MenuManager submenu = new MenuManager(Messages.OpenWorkspaceMenu_Open);
		submenu.add(new Action(Messages.OpenWorkspaceMenu_Advanced) {

			@Override
			public void run() {
				LaunchOptionsDialog dialog = new LaunchOptionsDialog(Display.getCurrent().getActiveShell());
				if (dialog.open() == Window.OK) {
					File application = LauncherPlugin.getDefault().getLauncherApplication();
					if (application != null) {
						String workspace = dialog.getWorkspaceLocation();
						JRE vm = dialog.getJVm();
						String cmd = System.getProperty(LauncherPlugin.PROP_COMMANDS);
						// Signal that vm arguments will be overridden.
						cmd = cmd + " --launcher.overrideVmargs"; //$NON-NLS-1$
						// Attempt to figure out the name of the Eclipse
						// launcher and it's corresponding ".ini" file
						String launcher = System.getProperty("eclipse.launcher"); //$NON-NLS-1$
						File inifile = new File(launcher + ".ini"); //$NON-NLS-1$
						try {
							EclipseConfiguration ec = new EclipseConfiguration(new FileInputStream(inifile));
							if (dialog.isDisableSmallFonts()) {
								ec.removeVmSetting("-Dorg.eclipse.swt.internal.carbon.smallFonts"); //$NON-NLS-1$
							}
							if (dialog.isClean()) {
								cmd = cmd + " -clean"; //$NON-NLS-1$
							}
							ec.setVmXmx(dialog.getXmx());
							ec.setVmXms(dialog.getXms());
							if (!dialog.getDebugMode().equals(DebugMode.Normal)) {
								ec.setRemoteDebug(dialog.getDebugPort(), dialog.getDebugMode()
										.equals(DebugMode.Suspend));
							}

							LauncherPlugin.getDefault().doLaunch(workspace, application, cmd, ec.toString(),
									vm.getPath());
						} catch (LaunchException | IOException e) {
							IStatus newStatus = new Status(IStatus.ERROR, LauncherPlugin.PLUGIN_ID,
									"Could not start new Eclipse instance", e); //$NON-NLS-1$
							StatusManager.getManager().handle(newStatus);
						}
					}
				}
			}

		});
		submenu.add(new Separator());
		IContributionItem[] items = getContributionItems(serviceLocator);
		for (IContributionItem iContributionItem : items) {
			submenu.add(iContributionItem);
		}
		additions.addContributionItem(submenu, null);
	}

	/**
	 * @param serviceLocator
	 *            the service locator
	 * @param label
	 *            label for the workspace
	 * @param workspace
	 *            path to the workspace
	 * @return
	 */
	public CommandContributionItem createOpenCommand(IServiceLocator serviceLocator, String label, String workspace) {
		CommandContributionItemParameter p = new CommandContributionItemParameter(serviceLocator, "", //$NON-NLS-1$
				COMMAND_ID, SWT.PUSH);
		if (workspace != null) {
			Map<Object, Object> parameters = new HashMap<Object, Object>();
			parameters.put(OpenWorkspaceHandler.WORKSPACE_PARAMETER_ID, workspace);
			p.parameters = parameters;
		}
		p.label = label;
		CommandContributionItem item = new CommandContributionItem(p);
		item.setVisible(true);
		return item;
	}

}
