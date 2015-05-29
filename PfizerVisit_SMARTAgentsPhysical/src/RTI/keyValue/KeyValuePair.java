package RTI.keyValue;


/*
  WARNING: THIS FILE IS AUTO-GENERATED. DO NOT MODIFY.

  This file was generated from .idl using "rtiddsgen".
  The rtiddsgen tool is part of the RTI Connext distribution.
  For more information, type 'rtiddsgen -help' at a command shell
  or consult the RTI Connext manual.
*/
    

import com.rti.dds.infrastructure.*;
import com.rti.dds.infrastructure.Copyable;

import java.io.Serializable;
import com.rti.dds.cdr.CdrHelper;


public class KeyValuePair implements Copyable, Serializable
{

    public String key = ""; /* maximum length = (60000) */
    public String source = ""; /* maximum length = (60000) */
    public String value = ""; /* maximum length = (60000) */


    public KeyValuePair() {

    }


    public KeyValuePair(KeyValuePair other) {

        this();
        copy_from(other);
    }



    public static Object create() {
        KeyValuePair self;
        self = new KeyValuePair();
         
        self.clear();
        
        return self;
    }

    public void clear() {
        
        key = "";
            
        source = "";
            
        value = "";
            
    }

    public boolean equals(Object o) {
                
        if (o == null) {
            return false;
        }        
        
        

        if(getClass() != o.getClass()) {
            return false;
        }

        KeyValuePair otherObj = (KeyValuePair)o;



        if(!key.equals(otherObj.key)) {
            return false;
        }
            
        if(!source.equals(otherObj.source)) {
            return false;
        }
            
        if(!value.equals(otherObj.value)) {
            return false;
        }
            
        return true;
    }

    public int hashCode() {
        int __result = 0;

        __result += key.hashCode();
                
        __result += source.hashCode();
                
        __result += value.hashCode();
                
        return __result;
    }
    

    /**
     * This is the implementation of the <code>Copyable</code> interface.
     * This method will perform a deep copy of <code>src</code>
     * This method could be placed into <code>KeyValuePairTypeSupport</code>
     * rather than here by using the <code>-noCopyable</code> option
     * to rtiddsgen.
     * 
     * @param src The Object which contains the data to be copied.
     * @return Returns <code>this</code>.
     * @exception NullPointerException If <code>src</code> is null.
     * @exception ClassCastException If <code>src</code> is not the 
     * same type as <code>this</code>.
     * @see com.rti.dds.infrastructure.Copyable#copy_from(java.lang.Object)
     */
    public Object copy_from(Object src) {
        

        KeyValuePair typedSrc = (KeyValuePair) src;
        KeyValuePair typedDst = this;

        typedDst.key = typedSrc.key;
            
        typedDst.source = typedSrc.source;
            
        typedDst.value = typedSrc.value;
            
        return this;
    }


    
    public String toString(){
        return toString("", 0);
    }
        
    
    public String toString(String desc, int indent) {
        StringBuffer strBuffer = new StringBuffer();        
                        
        
        if (desc != null) {
            CdrHelper.printIndent(strBuffer, indent);
            strBuffer.append(desc).append(":\n");
        }
        
        
        CdrHelper.printIndent(strBuffer, indent+1);            
        strBuffer.append("key: ").append(key).append("\n");
            
        CdrHelper.printIndent(strBuffer, indent+1);            
        strBuffer.append("source: ").append(source).append("\n");
            
        CdrHelper.printIndent(strBuffer, indent+1);            
        strBuffer.append("value: ").append(value).append("\n");
            
        return strBuffer.toString();
    }
    
}

