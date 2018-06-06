/*******************************************************************************
 * Copyright (c) 2018 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.phoebus.applications.alarm.model.json;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.phoebus.applications.alarm.client.AlarmClientLeaf;
import org.phoebus.applications.alarm.client.AlarmClientNode;
import org.phoebus.applications.alarm.client.ClientState;
import org.phoebus.applications.alarm.model.AlarmTreeItem;
import org.phoebus.applications.alarm.model.AlarmTreeLeaf;
import org.phoebus.applications.alarm.model.BasicState;
import org.phoebus.applications.alarm.model.SeverityLevel;
import org.phoebus.applications.alarm.model.TitleDetail;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;

/** Read alarm model from JSON
 *  @author Kay Kasemir
 */
@SuppressWarnings("nls")
public class JsonModelReader
{
    // The following use 'Object' for the json node instead of actual
    // JsonNode to keep Jackson specifics within this package.
    // Later updates of the JsonModelReader/Writer will not affect code
    // that calls them.

    /** Parse JSON text for alarm item configuration
     *  @param json_text JSON test
     *  @return JSON object
     *  @throws Exception
     */
    public static Object parseAlarmItemConfig(final String json_text) throws Exception
    {
        try
        (
            final JsonParser jp = JsonModelWriter.mapper.getFactory().createParser(json_text);
        )
        {
            return JsonModelWriter.mapper.readTree(jp);
        }
    }

    /** Is this the configuration or alarm state for a leaf?
     *  @param json JSON returned by {@link #parseAlarmItemConfig(String)}
     *  @return <code>true</code> for {@link AlarmTreeLeaf}, <code>false</code> for {@link AlarmClientNode}
     */
    public static boolean isLeafConfigOrState(final Object json)
    {
        final JsonNode actual = (JsonNode) json;
        // Leaf config contains description
        // Leaf alarm state contains detail of AlarmState
        return actual.get(JsonTags.DESCRIPTION) != null  ||  actual.get(JsonTags.CURRENT_SEVERITY) != null;
    }

    /** Update configuration of alarm tree item
     *  @param node {@link AlarmTreeItem}
     *  @param json JSON returned by {@link #parseAlarmItemConfig(String)}
     *  @return <code>true</code> if configuration changed, <code>false</code> if there was nothing to update
     */
    public static boolean updateAlarmItemConfig(final AlarmTreeItem<?> node, final Object json)
    {
        final JsonNode actual = (JsonNode) json;
        boolean changed = updateAlarmNodeConfig(node, actual);
        if (node instanceof AlarmTreeLeaf)
            changed |= updateAlarmLeafConfig((AlarmTreeLeaf) node, actual);
        return changed;
    }

    /** Update general {@link AlarmTreeItem} settings */
    private static boolean updateAlarmNodeConfig(final AlarmTreeItem<?> node, final JsonNode json)
    {
        boolean changed = false;

        JsonNode jn = json.get(JsonTags.GUIDANCE);
        if (jn != null)
            changed |= node.setGuidance(parseTitleDetail(jn));

        jn = json.get(JsonTags.DISPLAYS);
        if (jn != null)
            changed |= node.setDisplays(parseTitleDetail(jn));

        jn = json.get(JsonTags.COMMANDS);
        if (jn != null)
            changed |= node.setCommands(parseTitleDetail(jn));

        jn = json.get(JsonTags.ACTIONS);
        if (jn != null)
            changed |= node.setActions(parseTitleDetail(jn));

        return changed;
    }

    private static List<TitleDetail> parseTitleDetail(final JsonNode array)
    {
        final List<TitleDetail> entries = new ArrayList<>(array.size());
        for (int i=0; i<array.size(); ++i)
        {
            final JsonNode info = array.get(i);

            JsonNode jn = info.get(JsonTags.TITLE);
            final String title = jn == null ? "" : jn.asText();

            jn = info.get(JsonTags.DETAILS);
            final String details = jn == null ? "" : jn.asText();

            entries.add(new TitleDetail(title, details));
        }
        return entries;
    }

    /** Update specifics of {@link AlarmTreeLeaf} */
    private static boolean updateAlarmLeafConfig(final AlarmTreeLeaf node, final JsonNode json)
    {
        // Is this a leaf configuration message?
        JsonNode jn = json.get(JsonTags.DESCRIPTION);
        if (jn == null)
            return false;

        boolean changed = node.setDescription(jn.asText());

        jn = json.get(JsonTags.ENABLED);
        changed |= node.setEnabled(jn == null ? true : jn.asBoolean());

        jn = json.get(JsonTags.LATCHING);
        changed |= node.setLatching(jn == null ? true : jn.asBoolean());

        jn = json.get(JsonTags.ANNUNCIATING);
        changed |= node.setAnnunciating(jn == null ? true : jn.asBoolean());

        jn = json.get(JsonTags.DELAY);
        changed |= node.setDelay(jn == null ? 0 : jn.asInt());

        jn = json.get(JsonTags.COUNT);
        changed |= node.setCount(jn == null ? 0 : jn.asInt());

        jn = json.get(JsonTags.FILTER);
        changed |= node.setFilter(jn == null ? "" : jn.asText());

        return changed;
    }

    public static boolean updateAlarmState(final AlarmTreeItem<?> node, final Object json)
    {
        final JsonNode actual = (JsonNode) json;
        if (node instanceof AlarmClientLeaf)
            return updateAlarmLeafState((AlarmClientLeaf) node, actual);
        if (node instanceof AlarmClientNode)
            return updateAlarmNodeState((AlarmClientNode) node, actual);
        return false;
    }

    public static SeverityLevel parseSeverity(final Object json)
    {
        final JsonNode actual = (JsonNode) json;
        final JsonNode jn = actual.get(JsonTags.SEVERITY);
        if (jn == null)
            return null;
        return SeverityLevel.valueOf(jn.asText());
    }

    private static boolean updateAlarmLeafState(final AlarmClientLeaf node, final JsonNode json)
    {
        SeverityLevel severity = SeverityLevel.UNDEFINED;
        String message = "<?>";
        String value = "<?>";
        Instant time = null;
        SeverityLevel current_severity = SeverityLevel.UNDEFINED;
        String current_message = "<?>";

        JsonNode jn = json.get(JsonTags.SEVERITY);
        if (jn == null)
            return false;
        severity = SeverityLevel.valueOf(jn.asText());

        jn = json.get(JsonTags.MESSAGE);
        if (jn != null)
            message = jn.asText();

        jn = json.get(JsonTags.VALUE);
        if (jn != null)
            value = jn.asText();

        jn = json.get(JsonTags.CURRENT_SEVERITY);
        if (jn != null)
            current_severity = SeverityLevel.valueOf(jn.asText());

        jn = json.get(JsonTags.CURRENT_MESSAGE);
        if (jn != null)
            current_message = jn.asText();

        jn = json.get(JsonTags.TIME);
        if (jn != null)
        {
            long secs = 0, nano = 0;
            JsonNode sub = jn.get(JsonTags.SECONDS);
            if (sub != null)
                secs = sub.asLong();
            sub = jn.get(JsonTags.NANO);
            if (sub != null)
                nano = sub.asLong();
            time = Instant.ofEpochSecond(secs, nano);
        }

        final ClientState state = new ClientState(severity, message, value, time, current_severity, current_message);
        return node.setState(state);
    }

    private static boolean updateAlarmNodeState(final AlarmClientNode node, final JsonNode json)
    {
        SeverityLevel severity = SeverityLevel.UNDEFINED;

        JsonNode jn = json.get(JsonTags.SEVERITY);
        if (jn == null)
            return false;
        severity = SeverityLevel.valueOf(jn.asText());

        // Compare alarm state with node's current state
        if (node.getState().severity == severity)
            return false;
        final BasicState state = new BasicState(severity);
        node.setState(state);

        return true;
    }
}
