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
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
    <xsl:output method="xml"/>
    <xsl:param name="sensors"/>
    <xsl:param name="languages"/>

    <xsl:template match="/*">
    	<div id="fill" class="container top" style="height: 100%">
    		<script src="https://cdnjs.cloudflare.com/ajax/libs/ace/1.4.1/ace.js" type="text/javascript" charset="utf-8"></script>
			<script src="https://cdnjs.cloudflare.com/ajax/libs/ace/1.4.1/ext-modelist.js" type="text/javascript" charset="utf-8"></script>
			<!--
			<script src="https://cdnjs.cloudflare.com/ajax/libs/require.js/2.2.0/require.js" type="text/javascript" charset="utf-8"></script>
    		-->
    		<script><xsl:comment>
				
				function resize()
				{	var height=$("#fill").height();
  					//console.log(height);
  					height=height-310;
  					if (height&lt;400) height=400;
  					$('#editor').css('height',height);
				}
    		
    			$(window).resize(function()
    			{	resize();
				});
				
				$(function() {
					
					resize();
					
					editor = ace.edit("editor", {
						mode: "ace/mode/groovy",
						theme: "ace/theme/chrome",
						tabSize: 4,
						useSoftTabs: false,
						showPrintMargin: false,
						
					});
    				//editor.setTheme("ace/theme/chrome");
    				//editor.getSession().setMode("ace/mode/groovy");
    				//editor.getSession().setTabSize(4);
    				//editor.session.setUseSoftTabs(false)
					
					var textarea = $('textarea[name="script"]');
					var initialValue=textarea.val();
					editor.getSession().setValue(initialValue);
					
					editor.getSession().on('change', function(){
						textarea.val(editor.getSession().getValue());
					});
				});
				
				function doDelete()
				{	location.href="/polestar/scripts/delete/<xsl:value-of select='id'/>";
				}
			</xsl:comment></script>
			
			<form method="POST" class="form-horizontal">
				<div class="form-group">
					<label class="col-sm-1 control-label" for="name">Name</label>
					<div class="col-sm-11">
						<input type="text" class="form-control" name="name" value="{name}"/>
					</div>
				</div>
				<div class="form-group">
					<label class="col-sm-1 control-label" for="triggers">Triggers</label>
					<div class="col-sm-11">
						<input type="text" class="form-control" name="triggers" value="{triggers}" placeholder="comma separated list of sensors that trigger script"/>
					</div>
				</div>
				<div class="form-group">
					<label class="col-sm-1 control-label" for="period">Periodicity</label>
					<div class="col-sm-5">
						<select name="period" class="form-control">
							<option value="">
								<xsl:if test="period=''"><xsl:attribute name="selected"/></xsl:if>
								None</option>
							<option value="1000">
								<xsl:if test="period='1000'"><xsl:attribute name="selected"/></xsl:if>
								1 second</option>
							<option value="5000">
								<xsl:if test="period='5000'"><xsl:attribute name="selected"/></xsl:if>
								5 seconds</option>
							<option value="30000">
								<xsl:if test="period='30000'"><xsl:attribute name="selected"/></xsl:if>
								30 seconds</option>
							<option value="60000">
								<xsl:if test="period='60000'"><xsl:attribute name="selected"/></xsl:if>
								1 minute</option>
							<option value="300000">
								<xsl:if test="period='300000'"><xsl:attribute name="selected"/></xsl:if>
								5 minutes</option>
							<option value="1800000">
								<xsl:if test="period='1800000'"><xsl:attribute name="selected"/></xsl:if>
								30 minutes</option>
							<option value="3600000">
								<xsl:if test="period='3600000'"><xsl:attribute name="selected"/></xsl:if>
								1 hour</option>
							<option value="21600000">
								<xsl:if test="period='21600000'"><xsl:attribute name="selected"/></xsl:if>
								6 hours</option>
							<option value="86400000">
								<xsl:if test="period='86400000'"><xsl:attribute name="selected"/></xsl:if>
								1 day</option>
							<option value="604800000">
								<xsl:if test="period='604800000'"><xsl:attribute name="selected"/></xsl:if>
								7 days</option>	
						</select>
					</div>
					<label class="col-sm-1 control-label" for="target">Target</label>
					<div class="col-sm-5">
						<select name="target" class="form-control">
							<xsl:variable name="sel" select="target"/>
							<option>
								<xsl:if test="target=''"><xsl:attribute name="selected"/></xsl:if>
								None</option>
							
							<xsl:for-each select="$sensors/sensors/sensor">
								<option value="{id}">
									<xsl:if test="$sel=id"><xsl:attribute name="selected"/></xsl:if>
									<xsl:value-of select="name"/>
								</option>
							</xsl:for-each>
						</select>
						
					</div>
				</div>
				
				<div class="form-group">
					<label class="col-sm-1 control-label" for="keywords">Keyword</label>
					<div class="col-sm-11">
						<input type="text" class="form-control" name="keywords" value="{keywords}" placeholder="comma separated list of keywords"/>
					</div>
				</div>
				<div class="form-group">
					<label class="col-sm-1 control-label" for="public">Access</label>
					<div class="col-sm-5">
						<select name="public" class="form-control">
							<option>
								<xsl:if test="public='secret'"><xsl:attribute name="selected"/></xsl:if>
								secret</option>
							<option>
								<xsl:if test="public='private'"><xsl:attribute name="selected"/></xsl:if>
								private</option>
							<option>
								<xsl:if test="public='guest'"><xsl:attribute name="selected"/></xsl:if>
								guest</option>
							<option>
								<xsl:if test="public='public'"><xsl:attribute name="selected"/></xsl:if>
								public</option>
						</select>

					</div>
				</div>
				<div class="form-group">
					<label class="col-sm-1 control-label" for="language">Language</label>
					<div class="col-sm-5">
						<select name="language" class="form-control">
							<xsl:variable name="sel" select="language"/>
							<xsl:for-each select="$languages/languages/language">
								<option value="{endpoint}">
									<xsl:if test="$sel=endpoint"><xsl:attribute name="selected"/></xsl:if>
									<xsl:value-of select="name"/>
								</option>
							</xsl:for-each>
						</select>
					</div>

				</div>
				<div class="form-group">
					<div id="editor" >
						<!--<xsl:value-of select="script"/>-->
					</div>
					<textarea name="script" style="display: none;">
						<xsl:value-of select="script"/>
					</textarea>
				</div>
				<div class="form-group">
					<button type="button" class="btn btn-danger" data-toggle="modal" data-target="#confirmDeleteDialog">
						<span class="glyphicon glyphicon-trash"></span>
						Delete
					</button>				
					<xsl:text> </xsl:text>
					<button type="submit" name="cancel" class="btn btn-default">Cancel</button>
					<xsl:text> </xsl:text>
					<button type="submit" name="save" class="btn btn-primary">Save</button>
					<xsl:text> </xsl:text>
					<button type="submit" name="execute" class="btn btn-success">
						<span class="glyphicon glyphicon-play"></span>
						Execute
					</button>
				</div>
			</form>
			
			<div class="modal" id="confirmDeleteDialog" tabindex="-1" role="dialog" aria-hidden="true">
				<div class="modal-dialog">
    				<div class="modal-content">
    					<div class="modal-body">
    						<div class="alert alert-danger">
    							<p>Please confirm you really want to delete <xsl:value-of select="name"/></p> 
    						</div>
						</div>
						<div class="modal-footer">
							<button type="button" class="btn btn-default" data-dismiss="modal">Cancel</button>
							<button type="button" class="btn btn-danger" data-dismiss="modal" onclick="javascript:doDelete()">Delete</button>
						</div>
					</div>
    			</div>
    		</div>
			
			    	
		</div>
    </xsl:template>
</xsl:stylesheet>
