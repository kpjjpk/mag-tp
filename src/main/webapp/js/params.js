var _groupForms = null;

$(() => {
    _groupForms = {
        template: $('#group-form-template').children()[0],
        container: $('#group-forms'),
        count: 0
    };

    $('#add-group-form').click(_addGroupForm);

    $('#simulation-params-form').submit(_onFormSubmit);
});

function _onFormSubmit(event) {
    // event.preventDefault();
    const form = $(this)
    const data = form.serializeObject();
    const cleanData = JSON.stringify(data, (key, value) => {
      if (value === '') {
        return undefined;
      }
      return value;
    });

    // FIXME: horrible hack to bypass not supporting extended urlEncoded forms
    $('<input>').attr({
        type: 'hidden',
        name: 'json',
        value: cleanData
    }).appendTo(form);
}

function _addGroupForm() {
    const groupForm = $(_groupForms.template).clone();
    const groupId = _groupForms.count;

    groupForm.attr('id', _groupFormId(groupId));
    $(groupForm).find('.group-name').val(`Grupo ${groupId}`);
    $(groupForm).find('input').each((_, input) => {
        const defaultName = $(input).attr('name');
        $(input).attr('name', `groups[${groupId}][${defaultName}]`);
    });
    $(groupForm).find('.delete-group-btn').click(_deleteGroupForm(groupId));

    _groupForms.count++;
    _groupForms.container.append(groupForm);
}

function _groupFormId(id){
    return `group-form-${id}`;
}

function _deleteGroupForm(id){
    return function() {
      const groupId = _groupFormId(id)
      $(`#${groupId}`).remove();
    }
}
