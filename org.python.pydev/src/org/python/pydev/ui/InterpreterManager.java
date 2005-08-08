/*
 * Created on May 17, 2005
 *
 * @author Fabio Zadrozny
 */
package org.python.pydev.ui;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Preferences;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.python.copiedfromeclipsesrc.JavaVmLocationFinder;
import org.python.pydev.core.IPythonNature;
import org.python.pydev.core.REF;
import org.python.pydev.plugin.PydevPlugin;
import org.python.pydev.runners.SimpleJythonRunner;
import org.python.pydev.runners.SimplePythonRunner;
import org.python.pydev.ui.pythonpathconf.InterpreterInfo;

import sun.misc.BASE64Decoder;


/**
 * Does not write directly in INTERPRETER_PATH, just loads from it and works with it.
 * 
 * @author Fabio Zadrozny
 */
public abstract class InterpreterManager implements IInterpreterManager {

    private Map exeToInfo = new HashMap();
    private Preferences prefs;

    /**
     * Constructor
     */
    public InterpreterManager(Preferences prefs) {
        this.prefs = prefs;
        prefs.setDefault(INTERPRETER_PATH, "");
    }
    
    /**
     * @throws NotConfiguredInterpreterException
     * @see org.python.pydev.ui.IInterpreterManager#getDefaultInterpreter()
     */
    public String getDefaultInterpreter() throws NotConfiguredInterpreterException {
        String[] interpreters = getInterpreters();
        if(interpreters.length > 0){
            return interpreters[0];
        }else{
            throw new NotConfiguredInterpreterException("Interpreter is not properly configured! Please go to window->preferences->PyDev->Python Interpreters and configure it.");
        }
    }

    /**
     * @see org.python.pydev.ui.IInterpreterManager#getInterpreters()
     */
    public String[] getInterpreters() {
        return getInterpretersFromPersistedString(prefs.getString(INTERPRETER_PATH));
    }
    
    /**
     * @see org.python.pydev.ui.IInterpreterManager#getDefaultInterpreterInfo(org.eclipse.core.runtime.IProgressMonitor)
     */
    public InterpreterInfo getDefaultInterpreterInfo(IProgressMonitor monitor) {
        String interpreter = getDefaultInterpreter();
        return getInterpreterInfo(interpreter, monitor);
    }
    
    /**
     * @see org.python.pydev.ui.IInterpreterManager#getInterpreterInfo(java.lang.String)
     */
    public InterpreterInfo getInterpreterInfo(String executable, IProgressMonitor monitor) {
        InterpreterInfo info = (InterpreterInfo) exeToInfo.get(executable);
        if(info == null){
            monitor.worked(5);
            //ok, we have to get the info from the executable (and let's cache results for future use...
    		try {
    	        File script = PydevPlugin.getScriptWithinPySrc("interpreterInfo.py");

                String output;
                boolean isJythonExecutable = isJythonExecutable(executable);
                if(isJythonExecutable){
                    output = new SimpleJythonRunner().runAndGetOutputWithJar(executable, REF.getFileAbsolutePath(script), null, null, null, monitor);
                }else{                
                    output = new SimplePythonRunner().runAndGetOutputWithInterpreter(executable, REF.getFileAbsolutePath(script), null, null, null, monitor);
                }
                
    	        info = InterpreterInfo.fromString(output);
    	        info.restoreCompiledLibs(monitor);
                
                if(isJythonExecutable){
                    //the executable is the jar itself
                    info.executableOrJar = executable;
                }
    	    } catch (Exception e) {
    	        PydevPlugin.log(e);
    	        //TODO: make dialog: unable to get info for file... 
    	        throw new RuntimeException(e);
    	    }
    	    if(info.executableOrJar != null && info.executableOrJar.trim().length() > 0){
    	        exeToInfo.put(info.executableOrJar, info);
    	        
    	    }else{
                String title = "Invalid interpreter:"+executable;
                String msg = "Unable to get information on interpreter!";
    	        String reason = "The interpreter: '"+executable+"' is not a valid python executable - info.executable found: "+info.executableOrJar;
    	        
                try {
                    ErrorDialog.openError(null, title, msg, new Status(Status.ERROR, PydevPlugin.getPluginID(), 0, reason, null));
                } catch (Error e) {
                    // ignore error comunication error
                }
    	        throw new RuntimeException(reason);
    	    }
        }
        return info;
    }

    /**
     * @param executable
     * @return
     */
    private boolean isJythonExecutable(String executable) {
        return executable.endsWith(".jar");
    }

    /**
     * @see org.python.pydev.ui.IInterpreterManager#addInterpreter(java.lang.String)
     */
    public String addInterpreter(String executable, IProgressMonitor monitor) {
        InterpreterInfo info = getInterpreterInfo(executable, monitor);
        return info.executableOrJar;
    }

    //little cache...
    private String persistedCache;
    private String [] persistedCacheRet;
    
    /**
     * @see org.python.pydev.ui.IInterpreterManager#getInterpretersFromPersistedString(java.lang.String)
     */
    public String[] getInterpretersFromPersistedString(String persisted) {

        if(persisted == null || persisted.trim().length() == 0){
            return new String[0];
        }
        
        if(persistedCache == null || persistedCache.equals(persisted) == false){
	        List ret = new ArrayList();

	        try {
		        List list = (List) IOUtils.getStrAsObj(persisted);
	            
	            for (Iterator iter = list.iterator(); iter.hasNext();) {
	                InterpreterInfo info = (InterpreterInfo) iter.next();
	                this.exeToInfo.put(info.executableOrJar, info);
	                ret.add(info.executableOrJar);
	            }
            } catch (Exception e) {
                PydevPlugin.log(e);
                
                //ok, some error happened (maybe it's not configured)
                return new String[0];
            }
            
	        persistedCache = persisted;
	        persistedCacheRet = (String[]) ret.toArray(new String[0]);
        }
        return persistedCacheRet;
    }

    /**
     * @see org.python.pydev.ui.IInterpreterManager#getStringToPersist(java.lang.String[])
     */
    public String getStringToPersist(String[] executables) {
        ArrayList list = new ArrayList();
        for (int i = 0; i < executables.length; i++) {
            Object info = this.exeToInfo.get(executables[i]);
            if(info!=null){
                list.add(info);
            }
        }
        return REF.getObjAsStr(list);
    }


    /**
     * @see org.python.pydev.ui.IInterpreterManager#hasInfoOnDefaultInterpreter(IPythonNature)
     */
    public boolean hasInfoOnDefaultInterpreter(IPythonNature nature) {
        try {
            
            if (nature.isJython()) {
                throw new RuntimeException("only the python interpreter can be asked for right now");
            }

            InterpreterInfo info = (InterpreterInfo) exeToInfo.get(getDefaultInterpreter());
            return info != null;
            
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public String getDefaultJavaLocation() {
        try {
            return JavaVmLocationFinder.findDefaultJavaExecutable().getCanonicalPath();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}

class IOUtils {
    /**
     * @param persisted
     * @return
     * @throws IOException
     * @throws ClassNotFoundException
     */
    public static Object getStrAsObj(String persisted) throws IOException, ClassNotFoundException {
        BASE64Decoder decoder = new BASE64Decoder();
        InputStream input = new ByteArrayInputStream(decoder.decodeBuffer(persisted));
        ObjectInputStream in = new ObjectInputStream(input);
        Object list = in.readObject();
        in.close();
        input.close();
        return list;
    }

}