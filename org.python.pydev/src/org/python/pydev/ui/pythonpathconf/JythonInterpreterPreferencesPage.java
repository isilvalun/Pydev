/*
 * License: Common Public License v1.0
 * Created on 08/08/2005
 * 
 * @author Fabio Zadrozny
 */
package org.python.pydev.ui.pythonpathconf;


import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.swt.widgets.Composite;
import org.python.pydev.plugin.PydevPlugin;
import org.python.pydev.ui.IInterpreterManager;

public class JythonInterpreterPreferencesPage extends InterpreterPreferencesPage{

    public String getTitle() {
        return "Jython Interpreters";
    }

    /**
     * @return the title that should be used above the interpreters editor.
     */
    protected String getInterpretersTitle() {
        return "Jython interpreters (e.g.: jython.jar)";
    }

    /**
     * @param p this is the composite that should be the interpreter parent
     * @return an interpreter editor (used to add/edit/remove the information on an editor)
     */
    protected InterpreterEditor getInterpreterEditor(Composite p) {
        return new JythonInterpreterEditor (getInterpretersTitle(), p, PydevPlugin.getPythonInterpreterManager());
    }

    @Override
    protected void doRestore(String defaultSelectedInterpreter, IProgressMonitor monitor) {
        monitor.beginTask("Restoring PYTHONPATH", IProgressMonitor.UNKNOWN);
        IInterpreterManager iMan = PydevPlugin.getPythonInterpreterManager();
        final InterpreterInfo info = iMan.getInterpreterInfo(defaultSelectedInterpreter, monitor);
        info.restorePythonpath(monitor); //that's it, info.modulesManager contains the SystemModulesManager
        
        monitor.done();
    }
}
