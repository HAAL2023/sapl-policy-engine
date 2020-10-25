import {html, PolymerElement} from '@polymer/polymer/polymer-element.js';
import '@vaadin/vaadin-ordered-layout/src/vaadin-vertical-layout.js';
import '@vaadin/vaadin-ordered-layout/src/vaadin-horizontal-layout.js';

class ShowSinglePip extends PolymerElement {

    static get template() {
        return html`
<style include="shared-styles">
                :host {
                    display: block;
                    height: 100%;
                }
            </style>
<vaadin-vertical-layout style="width: 100%; height: 100%; padding: var(--lumo-space-s);" theme="spacing-s">
 <vaadin-horizontal-layout style="align-self: stretch;" theme="spacing-s">
  <vaadin-text-area label="Name" placeholder="Add detailed explanation" id="nameTextArea" style="margin: var(--lumo-space-s); padding: var(--lumo-space-s);" readonly></vaadin-text-area>
  <vaadin-text-area label="Description" placeholder="Add detailed explanation" id="descriptionTextArea" style="margin: var(--lumo-space-s); padding: var(--lumo-space-s); flex-grow: 1;" readonly></vaadin-text-area>
 </vaadin-horizontal-layout>
 <vaadin-grid id="pipGrid" style="margin: var(--lumo-space-s); padding: var(--lumo-space-s); align-self: flex-start; flex-grow: 1;"></vaadin-grid>
</vaadin-vertical-layout>
`;
    }

    static get is() {
        return 'show-single-pip';
    }

    static get properties() {
        return {
            // Declare your properties here.
        };
    }
}

customElements.define(ShowSinglePip.is, ShowSinglePip);
