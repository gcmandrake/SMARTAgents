package RTI.ACL;


/*
  WARNING: THIS FILE IS AUTO-GENERATED. DO NOT MODIFY.

  This file was generated from .idl using "rtiddsgen".
  The rtiddsgen tool is part of the RTI Connext distribution.
  For more information, type 'rtiddsgen -help' at a command shell
  or consult the RTI Connext manual.
*/
    
import com.rti.dds.typecode.*;


public class TestACLTypeCode {
    public static final TypeCode VALUE = getTypeCode();

    private static TypeCode getTypeCode() {
        TypeCode tc = null;
        int i=0;
        StructMember sm[] = new StructMember[1];

        sm[i]=new StructMember("serialisedMessage",false,(short)-1,false,(TypeCode)new TypeCode(TCKind.TK_STRING,60000),0,false); i++;

        tc = TypeCodeFactory.TheTypeCodeFactory.create_struct_tc("TestACL",ExtensibilityKind.EXTENSIBLE_EXTENSIBILITY,sm);
        return tc;
    }
}
