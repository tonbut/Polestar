<?xml version="1.0" encoding="UTF-8" ?>
<!--
   Copyright 2015 1060 Research Ltd

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
-->
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xrl="http://netkernel.org/xrl">

    <xsl:template match="/log">
    	<div class="container top">
    		<table id="log">
    		<xsl:for-each select="record">
    			<xsl:sort data-type="text" select="date" order="descending"/>
    			<tr>
    				<xsl:choose>
    					<xsl:when test="level='WARNING'"><xsl:attribute name="style">background: #FE8;</xsl:attribute></xsl:when>
    					<xsl:when test="level='SEVERE'"><xsl:attribute name="style">background: #F98;</xsl:attribute></xsl:when>
    				</xsl:choose>
    				<td><xsl:value-of select="substring-after(date,'T')"/></td>
    				<td><xsl:value-of select="message"/></td>
    			</tr>
    		</xsl:for-each>
    		</table>
    	</div>
    </xsl:template>
    
</xsl:stylesheet>