/*
 * Copyright (c) Microsoft Corporation
 * <p/>
 * All rights reserved.
 * <p/>
 * MIT License
 * <p/>
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
 * to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 * <p/>
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 * <p/>
 * THE SOFTWARE IS PROVIDED *AS IS*, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 * THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.microsoft.azuretools.core.ui;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;

import com.microsoft.azuretools.adauth.AuthCanceledException;
import com.microsoft.azuretools.adauth.StringUtils;
import com.microsoft.azuretools.authmanage.AdAuthManager;
import com.microsoft.azuretools.authmanage.SubscriptionManager;
import com.microsoft.azuretools.authmanage.interact.AuthMethod;
import com.microsoft.azuretools.authmanage.models.AuthMethodDetails;
import com.microsoft.azuretools.authmanage.models.SubscriptionDetail;
import com.microsoft.azuretools.core.Activator;
import com.microsoft.azuretools.sdkmanage.AccessTokenAzureManager;
import org.eclipse.swt.widgets.Link;

public class SignInDialog extends TitleAreaDialog {
	private static ILog LOG = Activator.getDefault().getLog();
    private Text textAuthenticationFilePath;
    private Button rbtnInteractive;
    private Button rbtnAutomated;
    private Label lblAuthenticationFile;
    private Button btnBrowse;
    private Button btnCreateAuthenticationFile;
    private Label lblInteractiveInfo;
    private Label lblAutomatedInfo;
    
    private AuthMethodDetails authMethodDetails;
    private String accountEmail;
    FileDialog fileDialog;
    private Link link;

    public AuthMethodDetails getAuthMethodDetails() {
        return authMethodDetails;
    }

    /**
     * Create the dialog.
     * @param parentShell
     */
    public SignInDialog(Shell parentShell) {
        super(parentShell);
        setHelpAvailable(false);
        setShellStyle(SWT.DIALOG_TRIM | SWT.RESIZE | SWT.APPLICATION_MODAL);
    }
    
    public static SignInDialog go(Shell parentShell, AuthMethodDetails authMethodDetails) {
    	SignInDialog d = new SignInDialog(parentShell);
        d.authMethodDetails = authMethodDetails;
        d.create();
        if (d.open() == Window.OK) {
            return d;
        }
        return null;
    }
    
    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        super.createButtonsForButtonBar(parent);
        Button okButton = getButton(IDialogConstants.OK_ID);
        okButton.setText("Sign in");
    }

    /**
     * Create contents of the dialog.
     * @param parent
     */
    @Override
    protected Control createDialogArea(Composite parent) {
        setTitle("Azure Sign In");
        Composite area = (Composite) super.createDialogArea(parent);
        Composite container = new Composite(area, SWT.NONE);
        container.setLayout(new FillLayout(SWT.HORIZONTAL));
        container.setLayoutData(new GridData(GridData.FILL_BOTH));
        
        Composite composite = new Composite(container, SWT.NONE);
        composite.setLayout(new GridLayout(1, false));
        
        Group group = new Group(composite, SWT.NONE);
        group.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
        group.setText("Authentication Method");
        group.setLayout(new GridLayout(1, false));
        
        rbtnInteractive = new Button(group, SWT.RADIO);
        rbtnInteractive.setSelection(true);
        rbtnInteractive.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                enableAutomatedAuthControls(false);
            }
        });
        rbtnInteractive.setText("Interactive");
        
        Composite compositeInteractive = new Composite(group, SWT.NONE);
        GridData gd_compositeInteractive = new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1);
        gd_compositeInteractive.heightHint = 38;
        gd_compositeInteractive.widthHint = 66;
        compositeInteractive.setLayoutData(gd_compositeInteractive);
        compositeInteractive.setLayout(new GridLayout(1, false));
        
        lblInteractiveInfo = new Label(compositeInteractive, SWT.WRAP);
        GridData gd_lblInteractiveInfo = new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1);
        gd_lblInteractiveInfo.horizontalIndent = 11;
        lblInteractiveInfo.setLayoutData(gd_lblInteractiveInfo);
        lblInteractiveInfo.setText("You will manually sign in using your Azure credentials as needed.");
        
        rbtnAutomated = new Button(group, SWT.RADIO);
        rbtnAutomated.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                enableAutomatedAuthControls(true);
            }
        });
        rbtnAutomated.setText("Automated");
        
        Composite compositeAutomated = new Composite(group, SWT.NONE);
        compositeAutomated.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
        compositeAutomated.setLayout(new GridLayout(2, false));
        
        lblAutomatedInfo = new Label(compositeAutomated, SWT.WRAP | SWT.HORIZONTAL);
        lblAutomatedInfo.setEnabled(false);
        GridData gd_lblAutomatedInfo = new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1);
        gd_lblAutomatedInfo.widthHint = 483;
        gd_lblAutomatedInfo.horizontalIndent = 11;
        gd_lblAutomatedInfo.heightHint = 49;
        lblAutomatedInfo.setLayoutData(gd_lblAutomatedInfo);
        lblAutomatedInfo.setText("An authentication file with credentials for an Azure Active Directory service principal will be used for automated sign ins.");
        
        lblAuthenticationFile = new Label(compositeAutomated, SWT.NONE);
        lblAuthenticationFile.setEnabled(false);
        GridData gd_lblAuthenticationFile = new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1);
        gd_lblAuthenticationFile.horizontalIndent = 10;
        lblAuthenticationFile.setLayoutData(gd_lblAuthenticationFile);
        lblAuthenticationFile.setText("Authentication file:");
        new Label(compositeAutomated, SWT.NONE);
        
        textAuthenticationFilePath = new Text(compositeAutomated, SWT.BORDER | SWT.READ_ONLY);
        textAuthenticationFilePath.setEnabled(false);
        GridData gd_textAuthenticationFilePath = new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1);
        gd_textAuthenticationFilePath.horizontalIndent = 10;
        textAuthenticationFilePath.setLayoutData(gd_textAuthenticationFilePath);
        
        btnBrowse = new Button(compositeAutomated, SWT.NONE);
        btnBrowse.setEnabled(false);
        btnBrowse.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                doSelectCredFilepath();
            }
        });
        btnBrowse.setText("Browse...");
        new Label(compositeAutomated, SWT.NONE);
        
        btnCreateAuthenticationFile = new Button(compositeAutomated, SWT.NONE);
        btnCreateAuthenticationFile.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                doCreateServicePrincipal();
            }
        });
        btnCreateAuthenticationFile.setEnabled(false);
        btnCreateAuthenticationFile.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));
        btnCreateAuthenticationFile.setText("New...");
        
        link = new Link(compositeAutomated, SWT.NONE);
        link.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                try {
                    PlatformUI.getWorkbench().getBrowserSupport().
                    getExternalBrowser().openURL(new URL("https://go.microsoft.com/fwlink/?linkid=847862"));
                } catch (PartInitException | MalformedURLException ex) {
                    LOG.log(new Status(IStatus.ERROR, Activator.PLUGIN_ID, "widgetSelected@SelectionAdapter@link@SignInDialog", ex));
                }
            }
        });
        GridData gd_link = new GridData(SWT.FILL, SWT.FILL, false, false, 2, 1);
        gd_link.widthHint = 312;
        link.setLayoutData(gd_link);
        link.setText("Follow these manual <a>steps</a> if Azure Toolkit fails to create an authentication file programmatically.");
        
        fileDialog = new FileDialog(btnBrowse.getShell(), SWT.OPEN);
        fileDialog.setText("Select Authentication File");
        fileDialog.setFilterPath(System.getProperty("user.home"));
        fileDialog.setFilterExtensions(new String[]{"*.azureauth", "*.*"});

        return area;
    }
    
    private void enableAutomatedAuthControls(boolean enabled) {
        setErrorMessage(null);
        lblInteractiveInfo.setEnabled(!enabled);
        lblAutomatedInfo.setEnabled(enabled);
        lblAuthenticationFile.setEnabled(enabled);
        lblAuthenticationFile.setEnabled(enabled);
        textAuthenticationFilePath.setEnabled(enabled);
        btnBrowse.setEnabled(enabled);
        btnCreateAuthenticationFile.setEnabled(enabled);
    }

    @Override
    public void okPressed() {
//      dropCurrentAzureManager();
        AuthMethodDetails authMethodDetailsResult = new AuthMethodDetails();
      if (rbtnInteractive.getSelection()) {
          doSignIn();
          if (StringUtils.isNullOrEmpty(accountEmail)) {
              System.out.println("Canceled by the user.");
              return;
          }
          authMethodDetailsResult.setAuthMethod(AuthMethod.AD);
          authMethodDetailsResult.setAccountEmail(accountEmail);
      } else { // automated
          String authPath = textAuthenticationFilePath.getText();
          if (StringUtils.isNullOrWhiteSpace(authPath)) {
              this.setErrorMessage("Select authentication file");
              return;
          }

          authMethodDetailsResult.setAuthMethod(AuthMethod.SP);
          // TODO: check the file is valid
          authMethodDetailsResult.setCredFilePath(authPath);
      }
      
      this.authMethodDetails = authMethodDetailsResult;
      
      super.okPressed();
    }
    
    private void doSelectCredFilepath() {
        setErrorMessage(null);
        String path = fileDialog.open();
        if (path == null) return;
        textAuthenticationFilePath.setText(path);
    }

    private void doSignIn() {
        try {
            AdAuthManager adAuthManager = AdAuthManager.getInstance();
            if (adAuthManager.isSignedIn()) {
                doSignOut();
            }
            signInAsync();
            accountEmail = adAuthManager.getAccountEmail();
        } catch (IOException | InvocationTargetException | InterruptedException ex) {
            System.out.println("doSignIn@SingInDialog: " + ex.getMessage());
            ex.printStackTrace();
            LOG.log(new Status(IStatus.ERROR, Activator.PLUGIN_ID, "doSignIn@SingInDialog", ex));
        }
    }

    private void signInAsync() throws InvocationTargetException, InterruptedException  {
        IRunnableWithProgress op = new IRunnableWithProgress() {
            @Override
            public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
                monitor.beginTask("Signing In...", IProgressMonitor.UNKNOWN);
                try {
                    AdAuthManager.getInstance().signIn();
                } catch (AuthCanceledException ex) {
                    System.out.println(ex.getMessage());
                } catch (IOException ex) {
                    System.out.println("run@ProgressDialog@signInAsync@SingInDialog: " + ex.getMessage());
                    //ex.printStackTrace();
                    //LOG.log(new Status(IStatus.ERROR, Activator.PLUGIN_ID, "run@ProgressDialog@signInAsync@SingInDialog", e));
                    Display.getDefault().asyncExec(new Runnable() {
                        @Override
                        public void run() {
                            ErrorWindow.go(getShell(), ex.getMessage(), "Sign In Error");;
                        }
                    });
                }
            }
        };
        new ProgressMonitorDialog(this.getShell()).run(true, false, op);
    }

    private void doSignOut() {
        try {
            accountEmail = null;
            AdAuthManager.getInstance().signOut();
        } catch (IOException ex) {
            ex.printStackTrace();
            LOG.log(new Status(IStatus.ERROR, Activator.PLUGIN_ID, "doSignOut@SingInDialog", ex));
        }
    }
    
    private void doCreateServicePrincipal() {
        setErrorMessage(null);
        AdAuthManager adAuthManager = null;
        try {
            adAuthManager = AdAuthManager.getInstance();
            if (adAuthManager.isSignedIn()) {
                adAuthManager.signOut();
            }

            signInAsync();

            if (!adAuthManager.isSignedIn()) {
                // canceled by the user
                System.out.println(">> Canceled by the user");
                return;
            }

            AccessTokenAzureManager accessTokenAzureManager = new AccessTokenAzureManager();
            SubscriptionManager subscriptionManager = accessTokenAzureManager.getSubscriptionManager();
            
            IRunnableWithProgress op = new IRunnableWithProgress() {
                @Override
                public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
                    monitor.beginTask("Getting Subscription List...", IProgressMonitor.UNKNOWN);
                    try {
                        subscriptionManager.getSubscriptionDetails();
                    } catch (Exception ex) {
                        System.out.println("run@ProgressDialog@doCreateServicePrincipal@SignInDialog: " + ex.getMessage());
                        LOG.log(new Status(IStatus.ERROR, Activator.PLUGIN_ID, "run@ProgressDialog@doCreateServicePrincipal@SignInDialogg", ex));
                    }
                }
            };
            new ProgressMonitorDialog(this.getShell()).run(true, false, op);
            
            SrvPriSettingsDialog d = SrvPriSettingsDialog.go(this.getShell(), subscriptionManager.getSubscriptionDetails());
            List<SubscriptionDetail> subscriptionDetailsUpdated;
            String destinationFolder;
            if (d != null) {
                subscriptionDetailsUpdated = d.getSubscriptionDetails();
                destinationFolder = d.getDestinationFolder();
            } else {
                System.out.println(">> Canceled by the user");
                return;
            }
            
            Map<String, List<String>> tidSidsMap = new HashMap<>();
            for (SubscriptionDetail sd : subscriptionDetailsUpdated) {
                if (sd.isSelected()) {
                    System.out.format(">> %s\n", sd.getSubscriptionName());
                    String tid = sd.getTenantId();
                    List<String> sidList;
                    if (!tidSidsMap.containsKey(tid)) {
                        sidList = new LinkedList<>();
                    } else {
                        sidList = tidSidsMap.get(tid);
                    }
                    sidList.add(sd.getSubscriptionId());
                    tidSidsMap.put(tid, sidList);
                }
            }

            SrvPriCreationStatusDialog  d1 = SrvPriCreationStatusDialog.go(this.getShell(), tidSidsMap, destinationFolder);
            if (d1 == null) {
                System.out.println(">> Canceled by the user");
                return;
            }
            
            String path = d1.getSelectedAuthFilePath();
            if (path == null) {
                System.out.println(">> No file was created");
                return;
            }
            
            textAuthenticationFilePath.setText(path);
            fileDialog.setFilterPath(destinationFolder);
            
        } catch (Exception ex) {
            ex.printStackTrace();
            LOG.log(new Status(IStatus.ERROR, Activator.PLUGIN_ID, "doCreateServicePrincipal@SignInDialog", ex));
        } finally {
            if (adAuthManager != null) {
                adAuthManager.signOut();
            }
        }
    }
}
