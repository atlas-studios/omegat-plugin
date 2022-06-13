/**************************************************************************
 OmegaT - Computer Assisted Translation (CAT) tool
          with fuzzy matching, translation memory, keyword search,
          glossaries, and translation leveraging into updated projects.

 Copyright (C) 2012 Alex Buloichik, Didier Briel
               2014 Ilia Vinogradov
               2015,2021 Kos Ivantsov
               Home page: http://www.omegat.org/
               Support center: http://groups.yahoo.com/group/OmegaT/

 This file is part of OmegaT.

 OmegaT is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 OmegaT is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.
 **************************************************************************/

package com.atlasstudios.omegat;

import java.awt.GridBagConstraints;
import java.awt.Window;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.ResourceBundle;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.logging.Logger;

import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JLabel;
import javax.swing.JTextField;

import org.omegat.util.Log;
import org.omegat.gui.exttrans.MTConfigDialog;
import org.omegat.util.HttpConnectionUtils;
import org.omegat.util.Language;
import org.omegat.core.CoreEvents;
import org.omegat.core.Core;
import org.omegat.core.events.IApplicationEventListener;
import org.omegat.core.machinetranslators.BaseTranslate;
import org.omegat.core.machinetranslators.MachineTranslators;
import org.omegat.filters2.master.PluginUtils;
import org.omegat.gui.exttrans.IMTGlossarySupplier;
import org.omegat.gui.exttrans.IMachineTranslation;
import org.omegat.util.Preferences;
import org.omegat.util.OStrings;
import org.xml.sax.InputSource;

/**
 * Atlas Translate plugin for OmegaT (Caching translations).
 * @author Jeremy Oon
 */

@SuppressWarnings({"unchecked", "rawtypes"})
public class WebnovelTranslate extends BaseTranslate {

    // IMTGlossarySupplier glossarySupplier;
    public static final int ERR_OK = 200;
    public static final int ERR_KEY_INVALID = 401;
    public static final int ERR_KEY_BLOCKED = 402;
    public static final int ERR_DAILY_REQ_LIMIT_EXCEEDED = 403;
    public static final int ERR_DAILY_CHAR_LIMIT_EXCEEDED = 404;
    public static final int ERR_TEXT_TOO_LONG = 413;
    public static final int ERR_UNPROCESSABLE_TEXT = 422;
    public static final int ERR_LANG_NOT_SUPPORTED = 501;

    private static final String ALLOW_ATLAS_TRANSLATE = "allow_webnovel_translate";
    private static final String PROPERTY_USE_GLOSSARY = "atlas.use-glossary";
    private static final int MAX_GLOSSARY_TERMS = 50;
    protected static final String USER_AGENT = "Mozilla/5.0";

    private static final String PROPERTY_USER = "fakemt.email";
    private static final String PROPERTY_PASSWORD = "fakemt.password";
    private static final String PROPERTY_USER_DEFAULT = "";
    private static final String PROPERTY_PASSWORD_DEFAULT = "";    
    protected static final String PARAM_URL = "fakemt.url";
    protected static final String PARAM_URL_DEFAULT = "";
    protected static final String PARAM_BOOKCODE = "fakemt.bookcode";
    protected static final String PARAM_BOOKCODE_DEFAULT = "";
    protected static final String PARAM_NAME = "fakemt.name";
    protected static final String PARAM_TEXT = "fakemt.query.text";
    protected static final String PARAM_TEXT_DEFAULT = "text";
    protected static final String PARAM_GLOSSARY = "fakemt.glossary.format";
    protected static final String PARAM_GLOSSARY_DEFAULT = "true";
    protected static final String PARAM_GENRE_DEFAULT = "";
    protected static final String PARAM_EXPR = "fakemt.result.expr";
    protected static final String PARAM_EXPR_DEFAULT = "$.translation";

    protected static final ResourceBundle res = ResourceBundle.getBundle("AtlasTranslate", Locale.getDefault());

    public static String ReplaceString(String data){
        Pattern p = Pattern.compile("\\\\u(\\p{XDigit}{4})");
        Matcher m = p.matcher(data);
        StringBuffer buf = new StringBuffer(data.length());
        while (m.find()) {
            String ch = String.valueOf((char) Integer.parseInt(m.group(1), 16));
            m.appendReplacement(buf, Matcher.quoteReplacement(ch));
        }
        m.appendTail(buf);
        return buf.toString();
    }
    class WNPostResponse {

        public int code;
        public String response;
    }

    // Plugin setup
    public static void loadPlugins() {
        Core.registerMachineTranslationClass(WebnovelTranslate.class);
        CoreEvents.registerApplicationEventListener(new IApplicationEventListener() {
            @Override
            public void onApplicationStartup() {
                // MachineTranslators.add(new WebnovelTranslate());
            }
            @Override
            public void onApplicationShutdown() {
                /* empty */
            }
        });
    }

    public static void unloadPlugins() {
        /* empty */
    }

    @Override
    protected String getPreferenceName() {
        return ALLOW_ATLAS_TRANSLATE;
    }

    public String getName() {
        return res.getString("MT_ENGINE_ATLAS");
    }


    public String getGlossary() throws Exception {
        String params = "";
       
        if (Preferences.isPreference(PROPERTY_USE_GLOSSARY)) {
            List<IMachineTranslation> MT = MachineTranslators.getMachineTranslators();
            IMachineTranslation WNT = MT.get(MT.size()-1);

            Log.log("Loaded machine translation: " + WNT.getName());


            // WNT.setGlossarySupplier(glossarySupplier);
            Map<String, String> glossaryTerms = glossarySupplier.get();
            Log.log("Glossary: " + glossaryTerms);
            if (!glossaryTerms.isEmpty()) {
                params =  createGlossaryConfigPart(glossaryTerms);
            }
        }
        return params;
    }


    @Override
    protected String translate(Language sLang, Language tLang, String text) throws Exception {

        String lvSourceLang = sLang.getLanguageCode().substring(0, 2).toLowerCase(Locale.ENGLISH);
        String lvTargetLang = tLang.getLanguageCode().substring(0, 2).toLowerCase(Locale.ENGLISH);

        // U+2026 HORIZONTAL ELLIPSIS
        String lvShorText = text.length() > 10000 ? text.substring(0, 9999) + "\u2026" : text;
        String prev = getFromCache(sLang, tLang, lvShorText);

        if (prev != null) {
            return prev;
        }
        String userAuth = getCredential(PROPERTY_USER);
        String passAuth = getCredential(PROPERTY_PASSWORD);

        Map<String, String> p = new TreeMap<String, String>();

        if ((userAuth == null || userAuth.isEmpty()) && (passAuth == null || passAuth.isEmpty())) {
            throw new Exception(res.getString("MT_ENGINE_ATLAS_INVALID_KEY"));
        }
        else {
            p.put("email", userAuth);
            p.put("password", passAuth);
        }
        
        if (Preferences.getPreferenceDefault(PARAM_BOOKCODE, PARAM_BOOKCODE_DEFAULT).length() > 0) {
            p.put("bookcode", Preferences.getPreferenceDefault(PARAM_BOOKCODE, PARAM_BOOKCODE_DEFAULT));
        }
        // if (Preferences.getPreferenceDefault(PARAM_GENRE,"") != "") {
        //     lvShorText = "@@"+Preferences.getPreferenceDefault(PARAM_GENRE,"")+"@@" + lvShorText;
        // }
        p.put("text", lvShorText);
        p.put("glossary", getGlossary());

        Map<String, String> headers = new TreeMap<>();
        WNPostResponse response = new WNPostResponse();
        try {
            response.response =  HttpConnectionUtils.get(Preferences.getPreferenceDefault(PARAM_URL, PARAM_URL_DEFAULT), p, headers, "UTF-8");
        } catch (HttpConnectionUtils.ResponseError e) {
            response.response = null;
            response.code = e.code;
            Log.logErrorRB(Integer.toString(response.code));
        }
        if (response == null) {
            return null;
        }
        String tr = ReplaceString(response.response.substring(20, response.response.length() - 4)).replace("\\", "");
        // .replace("\\u2014","—").replace("\\u201C","“").replace("\\u201D","”").replace("\\u2019","’").replace("\\u2026","…").replace("\\", "")
        if (tr == null) {
            return null;
        }
        putToCache(sLang, tLang, lvShorText, tr);
        return tr;
    }

    protected WNPostResponse requestTranslate(Map params) throws Exception {
        WNPostResponse response = new WNPostResponse();
        Map<String, String> headers = new TreeMap<String, String>();
        try {
            response.response = HttpConnectionUtils.get(Preferences.getPreferenceDefault(PARAM_URL, PARAM_URL_DEFAULT), params, headers, "UTF-8");
            response.code = ERR_OK;
        } catch (HttpConnectionUtils.ResponseError ex) {
            response.response = null;
            response.code = ex.code;
        }
        return response;
    }
    private boolean isGlossary() {
        String value = System.getProperty(PARAM_GLOSSARY_DEFAULT,
                Preferences.getPreference(PARAM_GLOSSARY));
        return Boolean.parseBoolean(value);
    }
    @Override
    public boolean isConfigurable() {
        return true;
    }
    class ComboItem
    {
        private String key;
        private String value;
    
        public ComboItem(String key, String value)
        {
            this.key = key;
            this.value = value;
        }
    
        @Override
        public String toString()
        {
            return key;
        }
    
        public String getKey()
        {
            return key;
        }
    
        public String getValue()
        {
            return value;
        }
    }
    // @Override
    // public void showConfigurationUI(Window parent) {
    //     JPanel valuePanel = new JPanel();
    //     valuePanel.add(valueField);
    //     valuePanel.add(saveButton);
    //     valuePanel.add(refreshButton);
    //     JCheckBox glossaryCheckBox = new JCheckBox(res.getString("MT_ENGINE_ATLAS_GLOSSARY"));

    //     MTConfigDialog dialog = new MTConfigDialog(parent, getName()) {
    //         @Override
    //         protected void onConfirm() {
    //             boolean temporary = panel.temporaryCheckBox.isSelected();
    //             Preferences.setPreference(PARAM_URL, panel.valueField1.getText().trim());
    //             Preferences.setPreference(PARAM_BOOKCODE, panel.valueField2.getText().trim());
    //             Preferences.setPreference(PROPERTY_USER, panel.valueField3.getText().trim());
    //             String oAuthToken = panel.valueField4.getText().trim();
    //             setCredential(PROPERTY_PASSWORD, oAuthToken, temporary);
    //             Preferences.setPreference(PROPERTY_USE_GLOSSARY, glossaryCheckBox.isSelected());
    //             // Preferences.setPreference(PARAM_GENRE, (String) genreComboBox.getSelectedItem());
    //             // Preferences.setPreference(PARAM_GLOSSARY, "glossary");
    //         }
    //     };
    //     dialog.panel.valueLabel1.setText(res.getString("MT_ENGINE_ATLAS_URL"));
    //     dialog.panel.valueField1.setText(Preferences.getPreferenceDefault(PARAM_URL, PARAM_URL_DEFAULT));
    //     // dialog.panel.valueLabel2.setVisible(false);
    //     // dialog.panel.valueField2.setVisible(false);
    //     dialog.panel.valueLabel2.setText(res.getString("MT_ENGINE_ATLAS_BOOKCODE"));
    //     dialog.panel.valueField2.setText(Preferences.getPreferenceDefault(PARAM_BOOKCODE, PARAM_BOOKCODE_DEFAULT)); 

    //     dialog.panel.valueLabel3.setText(OStrings.getString("MT_ENGINE_ATLAS_USER"));
    //     dialog.panel.valueField3.setText(Preferences.getPreferenceDefault(PROPERTY_USER, PROPERTY_USER_DEFAULT));

    //     dialog.panel.valueLabel4.setText(OStrings.getString("MT_ENGINE_ATLAS_PASS"));
    //     dialog.panel.valueField4.setText(getCredential(PROPERTY_PASSWORD));

    //     glossaryCheckBox.setSelected(Preferences.isPreferenceDefault(PROPERTY_USE_GLOSSARY, true));
    //     // genreComboBox.setSelectedIndex(0);
    //     // dialog.panel.itemsPanel.add(genreComboBox);   
    //     dialog.panel.itemsPanel.add(glossaryCheckBox);   
    //     dialog.show();
    // }

    @Override
    public void showConfigurationUI(Window parent) {

        JPanel atlasPanel = new JPanel();
        atlasPanel.setLayout(new java.awt.GridBagLayout());
        atlasPanel.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 15, 0));
        atlasPanel.setAlignmentX(0.0F);

        // Info about IAM authentication
        JLabel iamAuthLabel = new JLabel(res.getString("MT_ENGINE_ATLAS_INFO"));
        GridBagConstraints gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 10, 5);
        atlasPanel.add(iamAuthLabel, gridBagConstraints);

        // API URL
        JLabel urlLabel = new JLabel(res.getString("MT_ENGINE_ATLAS_URL"));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 10, 5);
        atlasPanel.add(urlLabel, gridBagConstraints);

        JTextField urlField = new JTextField(Preferences.getPreferenceDefault(PARAM_URL, PARAM_URL_DEFAULT));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.ipadx = 50;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 10, 0);
        urlLabel.setLabelFor(urlField);
        atlasPanel.add(urlField, gridBagConstraints);

        // Custom Model
        JLabel modelIdLabel = new JLabel(res.getString("MT_ENGINE_ATLAS_BOOKCODE"));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 10, 5);
        atlasPanel.add(modelIdLabel, gridBagConstraints);

        JTextField modelIdField = new JTextField(Preferences.getPreferenceDefault(PARAM_BOOKCODE, ""));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.ipadx = 50;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 10, 0);
        modelIdLabel.setLabelFor(modelIdField);
        atlasPanel.add(modelIdField, gridBagConstraints);

        JCheckBox glossaryCheckBox = new JCheckBox(res.getString("MT_ENGINE_ATLAS_GLOSSARY"));

        MTConfigDialog dialog = new MTConfigDialog(parent, getName()) {
            @Override
            protected void onConfirm() {
                boolean temporary = panel.temporaryCheckBox.isSelected();

                String login = panel.valueField1.getText().trim();
                setCredential(PROPERTY_USER, login, temporary);

                String password = panel.valueField2.getText().trim();
                setCredential(PROPERTY_PASSWORD, password, temporary);

                System.setProperty(PARAM_BOOKCODE, modelIdField.getText());
                Preferences.setPreference(PARAM_BOOKCODE, modelIdField.getText());

                System.setProperty(PARAM_URL, urlField.getText());
                Preferences.setPreference(PARAM_URL, urlField.getText());

                Preferences.setPreference(PROPERTY_USE_GLOSSARY, glossaryCheckBox.isSelected());
            }
        };

        dialog.panel.valueLabel1.setText(res.getString("MT_ENGINE_ATLAS_USER"));
        dialog.panel.valueField1.setText(getCredential(PROPERTY_USER));

        dialog.panel.valueLabel2.setText(res.getString("MT_ENGINE_ATLAS_PASSWORD"));
        dialog.panel.valueField2.setText(getCredential(PROPERTY_PASSWORD));

        // TODO Apparently, the API URL can change if the user has their own instance.

        dialog.panel.temporaryCheckBox.setSelected(isCredentialStoredTemporarily(PROPERTY_PASSWORD));
        glossaryCheckBox.setSelected(Preferences.isPreferenceDefault(PROPERTY_USE_GLOSSARY, true));

          
        dialog.panel.itemsPanel.add(atlasPanel);
        dialog.panel.itemsPanel.add(glossaryCheckBox); 
        dialog.show();
    }


    /**
     * create glossary config part of request json.
     * we make visibility to protected for test purpose.
     * @param glossaryTerms glossary map.
     */
    public String createGlossaryConfigPart(Map<String, String> glossaryTerms) {
        List<GlossaryPair> pairs = new ArrayList<>();
        String glossaryText = "";
        Integer counter = 0;
        for (Map.Entry<String, String> e : glossaryTerms.entrySet()) {
            glossaryText += e.getKey() + "\t" + e.getValue() + "\n";
            counter++;
            if (counter >= MAX_GLOSSARY_TERMS) {
                break;
            }
        }
        return glossaryText;
    }

    /**
     * Json definition: glossaryPair.
     */
    static class GlossaryPair {
        public final String sourceText;
        public final String translatedText;
        GlossaryPair(String sourceText, String translatedText) {
            this.sourceText = sourceText;
            this.translatedText = translatedText;
        }
    }


}
