<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>

<c:if test="${not empty param.login_error}">
<div class="row">
	<p class="global-error alert alert-error span9">
		<fmt:message key="login.error"/>
		<!--
		<c:out value="${SPRING_SECURITY_LAST_EXCEPTION.message}"/>
		-->
	</p>
</div>
</c:if>

<div class="row">
	<p class="intro span9">
		<fmt:message key="login.intro">
			<fmt:param><c:url value="/associate"/></fmt:param>
		</fmt:message>
	</p>
</div>

<form class="form-horizontal" name="f" action="<c:url value='/j_spring_security_check'/>" class="login-form" method="post">
	<fieldset>
		<div class="control-group">
			<label class="control-label" for="login-username"><fmt:message key="user.username.label"/></label>
			<div class="controls">
				<input class="form-control" type="text" name="j_username" id="login-username" maxlength="240" 
					value="<c:if test='${not empty param.login_error}'>${SPRING_SECURITY_LAST_USERNAME}</c:if>"/>
			</div>
		</div>
		<div class="control-group">
			<label class="control-label" for="login-password"><fmt:message key="user.password.label"/></label>
			<div class="controls">
				<input class="form-control" type="password" name="j_password" id="login-password" maxlength="255" />
			</div>
		</div>
	</fieldset>
	<div class="control-group">
		<div class="controls">
			<button type="submit" class="btn btn-primary"><fmt:message key='login.label'/></button>
		</div>
	</div>
</form>
