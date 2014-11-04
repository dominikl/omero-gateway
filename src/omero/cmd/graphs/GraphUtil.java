/*
 * Copyright (C) 2014 University of Dundee & Open Microscopy Environment.
 * All rights reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package omero.cmd.graphs;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections.CollectionUtils;

import ome.services.graphs.GraphOpts.Op;
import omero.cmd.GraphModify;
import omero.cmd.GraphModify2;
import omero.cmd.Request;
import omero.cmd.graphOptions.ChildOption;

import com.google.common.base.Splitter;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;

/**
 * Static utility methods for model graph operations.
 * @author m.t.b.carroll@dundee.ac.uk
 * @since 5.1.0
 */
public class GraphUtil {
    /**
     * Split a list of strings by a given separator, trimming whitespace and ignoring empty items.
     * @param separator the separator between the list items
     * @param list the list
     * @return a means of iterating over the list items
     */
    static Iterable<String> splitList(char separator, String list) {
        return Splitter.on(separator).trimResults().omitEmptyStrings().split(list);
    }

    /**
     * Count how many objects are listed in a {@code IdListMap}.
     * @param idListMap lists of object IDs indexed by type name
     * @return how many objects are listed in given {@code IdListMap}
     */
    static int getIdListMapSize(Map<?, long[]> idListMap) {
        int size = 0;
        for (final long[] ids : idListMap.values()) {
            size += ids.length;
        }
        return size;
    }

    /**
     * Copy the given collection of IDs to an array of native {@code long}s.
     * @param ids a collection of IDs, none of which may be {@code null}
     * @return the same IDs in a new array
     */
    static long[] idsToArray(Collection<Long> ids) {
        final long[] idArray = new long[ids.size()];
        int index = 0;
        for (final long id : ids) {
            idArray[index++] = id;
        }
        return idArray;
    }

    /**
     * Copy the {@link GraphModify2} fields of one request to another.
     * @param requestFrom the source of the field copy
     * @param requestTo the target of the field copy
     */
    static void copyFields(GraphModify2 requestFrom, GraphModify2 requestTo) {
        requestTo.targetObjects = requestFrom.targetObjects == null ? null : new HashMap<String, long[]>(requestFrom.targetObjects);
        if (requestFrom.childOptions == null) {
            requestTo.childOptions = null;
        } else {
            requestTo.childOptions = new ChildOption[requestFrom.childOptions.length];
            for (int index = 0; index < requestFrom.childOptions.length; index++) {
                requestTo.childOptions[index] = new ChildOptionI((ChildOptionI) requestFrom.childOptions[index]);
            }
        }
        requestTo.dryRun = requestFrom.dryRun;
    }

    /**
     * Approximately translate {@link GraphModify} options in setting the parameters of a {@link GraphModify2} request.
     * @param graphRequestFactory a means of instantiating new child options
     * @param options {@link GraphModify} options, may be {@code null}
     * @param request the request whose options should be updated
     */
    static void translateOptions(GraphRequestFactory graphRequestFactory, Map<String, String> options,
            GraphModify2 request) {
        if (options == null) {
            return;
        }
        request.childOptions = new ChildOption[options.size()];
        int index = 0;
        for (final Map.Entry<String, String> option : options.entrySet()) {
            request.childOptions[index] = graphRequestFactory.createChildOption();
            /* find type to which options apply */
            String optionType = option.getKey();
            if (optionType.charAt(0) == '/') {
                optionType = optionType.substring(1);
            }
            for (final String optionValue : GraphUtil.splitList(';', option.getValue())) {
                /* approximately translate each option */
                if (Op.KEEP.toString().equals(optionValue)) {
                    request.childOptions[index].excludeType = Collections.singletonList(optionType);
                } else if (Op.HARD.toString().equals(optionValue)) {
                    request.childOptions[index].includeType = Collections.singletonList(optionType);
                } else if (optionValue.startsWith("excludes=")) {
                    if (request.childOptions[index].excludeNs == null) {
                        request.childOptions[index].excludeNs = new ArrayList<String>();
                    }
                    for (final String namespace : GraphUtil.splitList(',', optionValue.substring(9))) {
                        request.childOptions[index].excludeNs.add(namespace);
                    }
                }
            }
            index++;
        }
    }

    /**
     * Make a copy of a multimap with the full class names in the keys replaced by the simple class names.
     * @param entriesByFullName a multimap
     * @return a new multimap with the same contents, except for the package name having been trimmed off each key
     */
    static <X> SetMultimap<String, X> trimPackageNames(SetMultimap<String, X> entriesByFullName) {
        final SetMultimap<String, X> entriesBySimpleName = HashMultimap.create();
        for (final Map.Entry<String, Collection<X>> entriesForOneClass : entriesByFullName.asMap().entrySet()) {
            final String fullClassName = entriesForOneClass.getKey();
            final String simpleClassName = fullClassName.substring(fullClassName.lastIndexOf('.') + 1);
            final Collection<X> values = entriesForOneClass.getValue();
            entriesBySimpleName.putAll(simpleClassName, values);
        }
        return entriesBySimpleName;
    }

    /**
     * Combine consecutive facade requests with the same options into one request with the union of the target model objects.
     * Does not adjust {@link GraphModify2} requests because they already allow the caller to specify multiple target model objects
     * should they wish those objects to be processed together.
     * Call this method before calling {@link omero.cmd.IRequest#init(omero.cmd.Helper)} on the requests.
     * @param requests the list of requests to adjust
     */
    public static void combineFacadeRequests(List<Request> requests) {
        if (requests == null) {
            return;
        }
        int index = 0;
        while (index < requests.size() - 1) {
            final Request request1 = requests.get(index);
            final Request request2 = requests.get(index + 1);
            final boolean isCombined;
            if (request1 instanceof ChgrpFacadeI && request2 instanceof ChgrpFacadeI) {
                isCombined = isCombined((ChgrpFacadeI) request1, (ChgrpFacadeI) request2);
            } else if (request1 instanceof DeleteFacadeI && request2 instanceof DeleteFacadeI) {
                isCombined = isCombined((DeleteFacadeI) request1, (DeleteFacadeI) request2);
            } else {
                isCombined = false;
            }
            if (isCombined) {
                requests.remove(index + 1);
            } else {
                index++;
            }
        }
    }

    /**
     * Test if the maps have the same contents, regardless of ordering.
     * {@code null} arguments are taken as being empty maps.
     * @param map1 the first map
     * @param map2 the second map
     * @return if the two maps have the same contents
     */
    private static <K, V> boolean isEqualMaps(Map<K, V> map1, Map<K, V> map2) {
        if (map1 == null) {
            map1 = Collections.emptyMap();
        }        
        if (map2 == null) {
            map2 = Collections.emptyMap();
        }
        return CollectionUtils.isEqualCollection(map1.entrySet(), map2.entrySet());
    }

    /**
     * Combine the two chgrp requests should they be sufficiently similar.
     * @param chgrp1 the first request
     * @param chgrp2 the second request
     * @return if the target model object of the second request was successfully merged into those of the first request
     */
    private static boolean isCombined(ChgrpFacadeI chgrp1, ChgrpFacadeI chgrp2) {
        if (chgrp1.type.equals(chgrp2.type) &&
            isEqualMaps(chgrp1.options, chgrp2.options) &&
            chgrp1.grp == chgrp2.grp) {
            chgrp1.addToTargets(chgrp2.type, chgrp2.id);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Combine the two delete requests should they be sufficiently similar.
     * @param delete1 the first request
     * @param delete2 the second request
     * @return if the target model object of the second request was successfully merged into those of the first request
     */
    private static boolean isCombined(DeleteFacadeI delete1, DeleteFacadeI delete2) {
        if (delete1.type.equals(delete2.type) &&
            isEqualMaps(delete1.options, delete2.options)) {
            delete1.addToTargets(delete2.type, delete2.id);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Find the first class-name in a {@code /}-separated string.
     * @param type a type path in the style of the original graph traversal code
     * @return the first type found in the path
     */
    static String getFirstClassName(String type) {
        while (type.charAt(0) == '/') {
            type = type.substring(1);
        }
        final int firstSlash = type.indexOf('/');
        if (firstSlash > 0) {
            type = type.substring(0, firstSlash);
        }
        return type;
    }
}
