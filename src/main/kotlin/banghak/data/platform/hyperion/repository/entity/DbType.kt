package banghak.data.platform.hyperion.repository.entity

enum class DbType(val driverClassName: String) {
    MYSQL("com.mysql.cj.jdbc.Driver"),
    MARIADB("org.mariadb.jdbc.Driver"),
    ORACLE("oracle.jdbc.OracleDriver"),
    POSTGRESQL("org.postgresql.Driver"),
    MSSQL("com.microsoft.sqlserver.jdbc.SQLServerDriver")
}

