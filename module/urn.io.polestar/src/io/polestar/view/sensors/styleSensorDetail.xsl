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
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:nk="http://1060.org">
    <xsl:output method="xml"/>
    
    <xsl:template match="/sensor">
    		<div class="container sensorDetail">
 			<xsl:call-template name="js"/>
 			<div class="row"><div class="col-sm-12">
	    			<table><tr>
	    				<td><img src="{defn/icon}"/></td>
	    				<td><h1><xsl:value-of select="defn/name"/></h1><div class="label label-default"><xsl:value-of select="id"/></div></td>
	    			</tr></table>
	    		</div></div>
 			<div class="row">
	 			<div class="col-sm-12">
	    				<div id="exampleChart"/>
	    			</div>
	    		</div>
	    		
	    		<div class="row">
	 			<div class="col-sm-12">
	 				<div class="btn-group" role="group" id="periods">
						<button id="hour" type="button" class="btn btn-default">hour</button>
						<button id="day" type="button" class="btn btn-default">day</button>
						<button id="week" type="button" class="btn btn-default">week</button>
						<button id="month" type="button" class="btn btn-default">month</button>
						<button id="year" type="button" class="btn btn-default">year</button>
					</div>
	 			</div>
	 		</div>
	 		
	 		<div style="margin-top: 0.5em;">
	 			<div class="row"><label class="col-xs-3">Value</label><div class="col-xs-9"><xsl:value-of select="valueHuman"/><xsl:text> </xsl:text><xsl:value-of select="defn/units"/></div></div>
	 			<div class="row"><label class="col-xs-3">Error</label><div class="col-xs-9"><xsl:value-of select="error"/></div></div>
	 			<div class="row"><label class="col-xs-3">Constraints</label><div class="col-xs-9">
	 				<xsl:choose><xsl:when test="constraints/constraint">
	 				<xsl:for-each select="constraints/constraint">
						<xsl:text> </xsl:text>
						<span class="label label-danger">
							<xsl:value-of select="name" /><xsl:text> </xsl:text>(<xsl:value-of select="value" />)
						</span>
					</xsl:for-each>
					</xsl:when><xsl:otherwise>None</xsl:otherwise></xsl:choose>
	 			</div></div>
	 			<div class="row"><label class="col-xs-3">Keywords</label><div class="col-xs-9">
	 				<xsl:choose><xsl:when test="keywordList/keyword">
					<xsl:for-each select="keywordList/keyword">
						<xsl:text> </xsl:text>
						<span class="label label-info">
							<xsl:value-of select="." />
						</span>
					</xsl:for-each>
					</xsl:when><xsl:otherwise>None</xsl:otherwise></xsl:choose>
	 			</div></div>
	 			<div class="row"><label class="col-xs-3">Last Modified</label><div class="col-xs-9"><xsl:value-of select="lastModifiedHuman"/></div></div>
	 			<div class="row"><label class="col-xs-3">Last Updated</label><div class="col-xs-9"><xsl:value-of select="lastUpdatedHuman"/></div></div>
	 			<div class="row"><label class="col-xs-3">Record Count</label><div class="col-xs-9"><xsl:value-of select="info/count"/></div></div>
	 			<div class="row"><label class="col-xs-3">Record Size</label><div class="col-xs-9"><xsl:value-of select="info/avgSize"/> bytes</div></div>
	 			<div class="row"><label class="col-xs-3">Total Size</label><div class="col-xs-9"><xsl:value-of select="format-number((info/size) div 1024,'#.0')"/> Kb</div></div>
	 			<div class="row"><label class="col-xs-3">First Data</label><div class="col-xs-9"><xsl:value-of select="info/first"/></div></div>
	 		</div>
 			
		</div>
    </xsl:template>
    
    <xsl:template name="js">
		<script type="text/javascript" src="/polestar/pub/protovis-d3.2.js"></script>
    		<script><xsl:comment>
    		
    		var period="day";
    		
    		$(document).ready(function() {
    			setupButtons();
    			updateButtons();
    			renderChart();
    		});
    		
    		function updateButtons()
    		{	$("#periods").find("button").removeClass("active");
    			$("#periods").find("#"+period).addClass("active");
    		}
    		
    		function setupButtons()
    		{	$("#periods").find("button").click( function() {
    				period=this.id;
    				updateButtons();
    				renderChart();
    			});
    		}
    		
    		function renderChart()
		{
			var val= {};
			val.width=$(window).width();
			val.width2 = document.getElementById('exampleChart').offsetWidth;
			val.period=period;
			
			var jsonData=JSON.stringify(val, null, "  ");
			console.log(jsonData);
			var webId="<xsl:value-of select="webId"/>";
			$.post("/polestar/sensors/detailChart/"+webId,jsonData,function(d) {
				$("#exampleChart").html(d);
			});
		}
		
    		</xsl:comment></script>
    </xsl:template>
    
</xsl:stylesheet>