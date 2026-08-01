# Android form drafts and templates 0.17.0

## Automatic drafts

Every editable server action form saves its current values locally while the
user types. Drafts are separated by application, role and action card.

Closing a form does not discard its draft. Opening the same action later
restores the saved values and shows the draft timestamp.

A successful server action clears its draft. Failed actions keep the values so
the user can correct and retry them.

## Named templates

The form menu supports up to twelve templates per action and role.

Users can:

- save current values under a chosen name;
- apply an existing template;
- replace a template by saving with the same name;
- delete individual templates;
- restore the form defaults;
- clear the current draft.

Templates remain available after application restarts and are not removed when
a server action succeeds.

## Privacy

Drafts and templates are stored in application-private SharedPreferences. They
are not uploaded until the user submits the corresponding server action.
