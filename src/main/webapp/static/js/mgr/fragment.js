var editor;
var upeditor;
$(document).ready(
	function() {
		var spaceId = $("#pageFormSpaceId").val();
		if(spaceId != undefined){
			$("#query-space-checkbox").prop("checked",true);
			$("#space").val(spaceId).show();
		}
		var global = $("#pageFormGlobal").val();
		if(global && global == "true"){
			$("#query-global").prop("checked",true);
		}
		
		var callable = $("#pageFormCallable").val();
		if(callable && callable == "true"){
			$("#query-callable").prop("checked",true);
		}
		
		$("#query-space-checkbox").click(function(){
			$("#space").toggle();
		})
		$("#query-btn").click(function(){
			var form = "";
			$("#query-form").remove();
			form += '<form id="query-form" style="display:none" action="'+basePath+'/mgr/template/fragment/index" method="get">';
			var name = $.trim($("#query-name").val());
			if(name != ''){
				form += '<input type="hidden" name="name" value="'+name+'"/>';
			}
			if($("#query-space-checkbox").is(":checked")){
				form += '<input type="hidden" name="space.id" value="'+$("#space").val()+'"/>';
			}
			if($("#query-global").is(":checked")){
				form += '<input type="hidden" name="global" value="true"/>';
			}
			if($("#query-callable").is(":checked")){
				form += '<input type="hidden" name="callable" value="true"/>';
			}
			form += '</form>';
			$("body").append(form);
			$("#query-form").submit();
		})
		
		var mixedMode = {
		        name: "htmlmixed",
		        scriptTypes: [{matches: /\/x-handlebars-template|\/x-mustache/i,
		                       mode: null},
		                      {matches: /(text|application)\/(x-)?vb(a|script)/i,
		                       mode: "vbscript"}]
		      };
	    
	    upeditor = CodeMirror.fromTextArea(document.getElementById("upeditor"), {
	        mode: mixedMode,
	        lineNumbers: true,
	        autoCloseTags: true,
	        extraKeys: {"Ctrl-Space": "autocomplete"}
	      });
	    upeditor.setSize('100%',400);
		$("[data-page]").click(function(){
			var page = $(this).attr("data-page");
			$("#pageForm").find("input[name='currentPage']").val(page);
			$("#pageForm").submit();
		})
		$('[data-toggle="tooltip"]').tooltip();
		$("#createFragmentModal").on("show.bs.modal", function() {
			clearTip();
			$("#createFragmentModal").find("form")[0].reset();
		}).on("shown.bs.modal",function(){
			if(!editor){
				editor = CodeMirror.fromTextArea(document.getElementById("editor"), {
			        mode: mixedMode,
			        lineNumbers: true,
			        autoCloseTags: true,
			        extraKeys: {"Ctrl-Space": "autocomplete"}
			      });
			    editor.setSize('100%',400);
			}
		}).on('hidden.bs.modal', function() {
		});
		$("#updateFragmentModal").on("show.bs.modal", function() {
			clearTip();
			$("#updateFragmentModal").find("form")[0].reset();
		}).on("shown.bs.modal",function(){
		}).on('hidden.bs.modal', function() {
		});
		$("#show-create").click(function(){
			$('#createFragmentModal').modal('show')
		})
	$("[data-action='remove']").click(function(){
		var me = $(this);
		bootbox.confirm("确定要删除吗?",function(result){
			if(!result){
				return ;
			}
			$.ajax({
				type : "post",
				url : basePath+"/mgr/template/fragment/delete",
				data : {"id":me.attr("data-id")},
				success : function(data){
					if(data.success){
						success(data.message);
						setTimeout(function(){
							window.location.reload();
						},500)
					} else {
						error(data.message);
					}
				},
				complete:function(){
				}
			});
		});
	});
		$("#updateFragmentModal").find("form").find("input[type=checkbox]").eq(0).change(function(){
			if($(this).prop("checked")){
				$("#updateFragmentModal").find("form").find("select[name=space]").parent().hide();
			}else{
				$("#updateFragmentModal").find("form").find("select[name=space]").parent().show();
			}
		})
		$("#createFragmentModal").find("form").find("input[type=checkbox]").eq(0).change(function(){
			if($(this).prop("checked")){
				$("#createFragmentModal").find("form").find("select[name=space]").parent().hide();
			}else{
				$("#createFragmentModal").find("form").find("select[name=space]").parent().show();
			}
		})
	$("[data-action='edit']").click(function(){
		$.get(basePath+"/mgr/template/fragment/get/"+$(this).attr("data-id"),{},function(data){
			if(!data.success){
				bootbox.alert("要更新的挂件不存在");
			} else {
				data = data.data;
				$("#updateFragmentModal").modal("show");
				var form = $("#updateFragmentModal").find('form');
				form.find("input[name='name']").val(data.name);
				form.find("input[name='id']").val(data.id);
				form.find("input[type=checkbox]").eq(0).prop("checked",data.global);
				form.find("input[type=checkbox]").eq(1).prop("checked",data.callable);
				if(data.space){
					form.find("select[name='space']").val(data.space.id);
				}
				if(data.global)
					$("#updateFragmentModal").find("form").find("select[name=space]").parent().hide();					
				form.find("textarea[name='description']").val(data.description);
				upeditor.setValue(data.tpl);
			}
		});
	});
	
	$("#updateFragment").click(
			function() {
				$("#updateFragment").prop("disabled", true);
				var data = $("#updateFragmentModal").find("form").serializeObject();
				delete data['upeditor-markdown-doc'];
				var space = data.space;
				delete data['space'];
				data.global = $("#updateFragmentModal").find("form").find("input[type=checkbox]").prop("checked");
				if(space != ''){
					data.space = {"id":space};
				}
				data.tpl = upeditor.getValue();
				$.ajax({
					type : "post",
					url : basePath + "/mgr/template/fragment/update",
					data : JSON.stringify(data),
					dataType : "json",
					contentType : 'application/json',
					success : function(data) {
						if (data.success) {
							success(data.message);
							setTimeout(function() {
								window.location.reload();
							}, 500)
						} else {
							error(data.message);
						}
					},
					complete : function() {
						$("#updateFragment").prop("disabled",
								false);
					}
				});
			});

	$("#createFragment").click(
			function() {
				$("#createFragment").prop("disabled", true);
				var data = $("#createFragmentModal").find("form").serializeObject();
				delete data['editor-markdown-doc'];
				var space = data.space;
				delete data['space'];
				if(space != ''){
					data.space = {"id":space};
				}
				data.global = $("#createFragmentModal").find("form").find("input[type=checkbox]").prop("checked");
				data.tpl = editor.getValue();
				$.ajax({
					type : "post",
					url : basePath + "/mgr/template/fragment/create",
					data : JSON.stringify(data),
					dataType : "json",
					contentType : 'application/json',
					success : function(data) {
						if (data.success) {
							success(data.message);
							setTimeout(function() {
								window.location.reload();
							}, 500)
						} else {
							error(data.message);
						}
					},
					complete : function() {
						$("#createFragment").prop("disabled",
								false);
					}
				});
			});
	});
