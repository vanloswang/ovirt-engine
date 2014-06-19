package org.ovirt.engine.core.searchbackend;

public class VdcUserConditionFieldAutoCompleter extends BaseConditionFieldAutoCompleter {
    public enum UserOrGroup {
        User,
        Group
    }

    public VdcUserConditionFieldAutoCompleter() {
        super();
        // Building the basic vervs Dict
        mVerbs.add("NAME");
        mVerbs.add("LASTNAME");
        mVerbs.add("USRNAME");
        mVerbs.add("LOGIN");
        mVerbs.add("DIRECTORY");
        mVerbs.add("DEPARTMENT");
        mVerbs.add("GROUP");
        mVerbs.add("TITLE");
        mVerbs.add("ACTIVE");
        mVerbs.add("ROLE");
        mVerbs.add("TAG");
        mVerbs.add("POOL");
        mVerbs.add("TYPE");

        // Building the autoCompletion Dict
        buildCompletions();
        // Building the types dict
        getTypeDictionary().put("NAME", String.class);
        getTypeDictionary().put("LASTNAME", String.class);
        getTypeDictionary().put("USRNAME", String.class);
        getTypeDictionary().put("LOGIN", String.class);
        getTypeDictionary().put("DIRECTORY", String.class);
        getTypeDictionary().put("DEPARTMENT", String.class);
        getTypeDictionary().put("TITLE", String.class);
        getTypeDictionary().put("GROUP", String.class);
        getTypeDictionary().put("ACTIVE", Boolean.class);
        getTypeDictionary().put("ROLE", String.class);
        getTypeDictionary().put("TAG", String.class);
        getTypeDictionary().put("POOL", String.class);
        getTypeDictionary().put("TYPE", UserOrGroup.class);

        // building the ColumnName Dict
        columnNameDict.put("NAME", "name");
        columnNameDict.put("LASTNAME", "surname");
        columnNameDict.put("USRNAME", "username");
        columnNameDict.put("LOGIN", "username");
        columnNameDict.put("DIRECTORY", "domain");
        columnNameDict.put("DEPARTMENT", "department");
        columnNameDict.put("TITLE", "role");
        columnNameDict.put("GROUP", "groups");
        columnNameDict.put("ACTIVE", "active");
        columnNameDict.put("ROLE", "mla_role");
        columnNameDict.put("TAG", "tag_name");
        columnNameDict.put("POOL", "vm_pool_name");
        columnNameDict.put("TYPE", "user_group");
        // Building the validation dict
        buildBasicValidationTable();
    }

    @Override
    public IAutoCompleter getFieldRelationshipAutoCompleter(String fieldName) {
            return StringConditionRelationAutoCompleter.INSTANCE;
    }

    @Override
    public String buildConditionSql(
        String fieldName,
        String customizedValue,
        String customizedRelation,
        String tableName,
        boolean caseSensitive) {
        if ("USRNAME".equals(fieldName) && customizedValue.contains("@")) {
            // When the given user name contains the at sign, we need to split it and compare it to two columns in the
            // database: the column containing the login name of the user and the column containg the name of the
            // directory.
            int index = customizedValue.lastIndexOf("@");
            String loginValue = customizedValue.substring(0, index) + "'";
            String directoryValue = "'" + customizedValue.substring(index + 1);
            String loginSql = buildConditionSql(
                "LOGIN",
                loginValue,
                customizedRelation,
                tableName,
                caseSensitive
            );
            String directorySql = buildConditionSql(
                "DIRECTORY",
                directoryValue,
                customizedRelation,
                tableName,
                caseSensitive
            );
            return "(" + loginSql + " AND " + directorySql + ")";
        }
        else {
            return super.buildConditionSql(
                fieldName,
                customizedValue,
                customizedRelation,
                tableName,
                caseSensitive
            );
        }
    }
}
