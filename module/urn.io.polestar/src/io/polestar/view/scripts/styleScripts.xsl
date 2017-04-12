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

	<xsl:param name="tags"/>
	<xsl:param name="filter" nk:class="java.lang.String"></xsl:param>
	

    <xsl:template match="/*">
    	<xsl:choose>
    		<xsl:when test="@filtered">
    			<xsl:call-template name="filtered-results"/>
    		</xsl:when>
    		<xsl:otherwise>
    			<xsl:call-template name="page"/>
    		</xsl:otherwise>
    	</xsl:choose>
    </xsl:template>
    
    <xsl:template name="page">
    	<div class="container top">
			<script src="/polestar/pub/jquery-ui.min.js"></script>
			<script src="/polestar/pub/jquery.ui.touch-punch.js"></script>
    		<script><xsl:comment>
    			var updateTimeout;
    			var scriptTextSearch=false;
    			
				$(function() {
					initialiseExecButtons();					
					resetSorting();
					
					$("#filter").keyup( function(evt) {
						clearTimeout(updateTimeout);
						e=evt.which;
						if ( e==39 || e==37 || e==17 || e==18 || e==224) {
						//Ignore certain keys
						}
						else
						{	updateTimeout=setTimeout("doFilterUpdate()", 250);
						}
					});
					
					if ($("#filter").val()!="")
                    {	doFilterUpdate();
                    }					
					
				});
				
				function initialiseExecButtons()
				{
					$(".execute").each(function() {
						var dthis=$(this);
						var id=dthis.attr('id');
						var scriptId=id.substring(8);
						$("#"+id).on( "click", function(e) {
							e.preventDefault();
							location.href="/polestar/scripts/execute/"+scriptId;
						});
					});
				}
				
				function resetSorting()
				{
					$( "#script-list" ).sortable({
						handle: '.handle',
						stop: function( event, ui ) {
							//console.log(ui.item.attr('href'));
							if ($(ui.item).attr('href'))
							{
								var href=ui.item.attr('href');
								var i=href.lastIndexOf('/');
								var elementId=href=href.substring(i+1);
								var position=ui.item.index();
								//console.log(elementId+" "+position);
								var url="/polestar/scripts/reorder/"+elementId+"/"+position
								$.get(url, function(data){
								},'json');
								return true;
							}
						},
					});
					$( "#element-list" ).disableSelection();
				}
				
				function doFilterUpdate()
				{	var f=$("#filter").val();
					$.get("/polestar/scripts/filtered?tts="+scriptTextSearch+"&amp;f="+f, function(d) {
						$("#script-list-container").empty();
						$("#script-list-container").html(d);
						initialiseExecButtons();
					},"html");	
				}
				
				function showUpload()
				{	$("#upload").css("display","");
				}
				
				function toggleTextSearch()
				{	active=$("#tts").hasClass("active");
					if (active) $("#tts").removeClass("active");
					else $("#tts").addClass("active");
					scriptTextSearch=!active;
				}
				
			</xsl:comment></script>
    	
			<div class="list-group-item list-header">
				<xsl:variable name="count" select="count(/scripts/script)"/>
				<table style="width: 100%"><tr>
					<td >
						<a class="btn btn-primary btn-sm" href="/polestar/scripts/new"><span class="glyphicon glyphicon-plus"></span><span>New</span></a>
						<span class="hidden-xs">
							<xsl:text> </xsl:text>
							<a class="btn btn-default" href="/polestar/scripts/backup" title="backup"><span class="glyphicon glyphicon-cloud-download"></span></a>
							<xsl:text> </xsl:text>
							<button type="button" class="btn btn-default" onclick="showUpload()" title="restore"><span class="glyphicon glyphicon-cloud-upload"></span></button>
							<xsl:text> </xsl:text>
							<button class="btn btn-default" title="search script contents" onclick="toggleTextSearch()" id="tts"><span class="glyphicon glyphicon-paperclip"></span></button>
						</span>
						</td>
					<td ><input id="filter" type="text" placeholder="Filter list of {$count} scripts" class="form-control" value="">
							<xsl:attribute name="value"><xsl:value-of select="$filter"/></xsl:attribute>
						</input>
						<input id="tts-field" type="hidden"/>
					</td>
				</tr>
				<tr id="upload" style="display: none"><td colspan="2" style="padding-top: 4px;">
					<div class="alert alert-info">Restore will not overwrite existing scripts</div>
					<form method="post" action="/polestar/scripts/restore" enctype="multipart/form-data" style="margin-bottom: 0;">
						<input style="display: inline-block;" type="file" name="uploaded"/><button class="btn btn-primary" type="submit"><span class="glyphicon glyphicon-cloud-upload"></span> restore</button>
					</form>
				</td></tr>
				</table>
				<div id="script-keywords"> <!--class="hidden-xs" -->
                	<xsl:for-each select="$tags/tags/keywords/keyword">
                		<a href="scripts?filter={.}"><span class="label label-info"><xsl:value-of select="."/></span></a>
                		<xsl:text> </xsl:text>
                	</xsl:for-each>
                	<xsl:for-each select="$tags/tags/triggers/trigger">
                		<a href="scripts?filter={.}"><span class="label label-success"><xsl:value-of select="."/></span></a>
                		<xsl:text> </xsl:text>
                	</xsl:for-each>
                </div>
			</div>
			<div id="script-list-container">
				<xsl:call-template name="filtered-results"/>
			</div>
		</div>
    </xsl:template>
    
    <xsl:template name="filtered-results">
    	<div class="list-group" id="script-list" >
			<xsl:choose>
				<xsl:when test="count(script)=0">
					<div class="list-group-item">
						No scripts exist
					</div>
				</xsl:when>
				<xsl:otherwise>
					<xsl:for-each select="script">
						<a href="/polestar/scripts/edit/{id}" class="list-group-item">
							<div class="pull-right">
                                <div class="btn btn-default handle">
                                    <span class="glyphicon glyphicon-sort"></span>
                                </div>
                                <xsl:text> </xsl:text>
								<button id="execute-{id}" class="btn btn-success execute"><span class="glyphicon glyphicon-play"></span></button>
							</div>
							<div>
								<span class="title"><xsl:value-of select="name"/></span>
                                <span class="hidden-xs">
                                    <xsl:for-each select="keywords/keyword">
                                        <xsl:text> </xsl:text>
                                        <span class="label label-info"><xsl:value-of select="."/></span>
                                    </xsl:for-each>
                                    <xsl:for-each select="triggers/trigger">
                                        <xsl:text> </xsl:text>
                                        <span class="label label-success"><xsl:value-of select="."/></span>
                                    </xsl:for-each>
                                </span>
							</div>
						</a>
					</xsl:for-each>
				</xsl:otherwise>
			</xsl:choose>
		</div>
    </xsl:template>
    
    
</xsl:stylesheet>