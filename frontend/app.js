(function () {
    const canvas = document.getElementById("render-canvas");
    const form = document.getElementById("load-form");
    const stepFileInput = document.getElementById("step-file-input");
    const fitViewButton = document.getElementById("fit-view-button");
    const statusEl = document.getElementById("status");
    const treeRootEl = document.getElementById("tree-root");
    const warningListEl = document.getElementById("warning-list");
    const sourceStepFileEl = document.getElementById("source-step-file");
    const schemaNamesEl = document.getElementById("schema-names");
    const nodeCountEl = document.getElementById("node-count");
    const selectedLabelEl = document.getElementById("selected-label");

    const engine = new BABYLON.Engine(canvas, true, { preserveDrawingBuffer: true, stencil: true });
    const scene = new BABYLON.Scene(engine);
    scene.clearColor = BABYLON.Color4.FromHexString("#f5efe4ff");

    const camera = new BABYLON.ArcRotateCamera(
        "camera",
        Math.PI / 2,
        Math.PI / 2.8,
        10,
        BABYLON.Vector3.Zero(),
        scene
    );
    camera.attachControl(canvas, true);
    camera.wheelDeltaPercentage = 0.01;
    camera.lowerRadiusLimit = 0.1;

    const hemiLight = new BABYLON.HemisphericLight("hemi", new BABYLON.Vector3(0.2, 1, 0.1), scene);
    hemiLight.intensity = 0.95;
    const dirLight = new BABYLON.DirectionalLight("dir", new BABYLON.Vector3(-1, -2, -1), scene);
    dirLight.position = new BABYLON.Vector3(12, 18, 10);
    dirLight.intensity = 0.7;

    const ground = BABYLON.MeshBuilder.CreateGround("ground", { width: 40, height: 40 }, scene);
    const groundMaterial = new BABYLON.StandardMaterial("ground-material", scene);
    groundMaterial.diffuseColor = BABYLON.Color3.FromHexString("#efe4cf");
    groundMaterial.specularColor = new BABYLON.Color3(0, 0, 0);
    groundMaterial.alpha = 0.85;
    ground.material = groundMaterial;
    ground.receiveShadows = true;

    const highlightLayer = new BABYLON.HighlightLayer("highlight", scene);
    const state = {
        currentSceneData: null,
        nodeMeshes: new Map(),
        activeNodeId: null
    };

    engine.runRenderLoop(function () {
        scene.render();
    });

    window.addEventListener("resize", function () {
        engine.resize();
    });

    const initialStepFile = new URLSearchParams(window.location.search).get("stepFile")
        || "/root/work/StepParser/examples/fan.stp";
    stepFileInput.value = initialStepFile;

    form.addEventListener("submit", async function (event) {
        event.preventDefault();
        await loadAssembly(stepFileInput.value.trim());
    });

    fitViewButton.addEventListener("click", function () {
        fitCameraToVisibleMeshes();
    });

    loadAssembly(initialStepFile).catch(function (error) {
        setStatus(error.message || "Failed to load initial STEP file.", true);
    });

    async function loadAssembly(stepFile) {
        if (!stepFile) {
            setStatus("Provide a STEP file path.", true);
            return;
        }

        setStatus("Loading assembly data...");
        clearLoadedContent();

        const response = await fetch("/api/assembly-scene?stepFile=" + encodeURIComponent(stepFile));
        const payload = await response.json();
        if (!response.ok) {
            throw new Error(payload.error || "Failed to load assembly scene.");
        }

        state.currentSceneData = payload;
        updateMetadata(payload);
        renderWarnings(payload.warnings || []);
        renderTree(payload.roots || []);
        await loadMeshesForScene(payload.roots || []);
        fitCameraToVisibleMeshes();
        setStatus("Loaded " + countNodes(payload.roots || []) + " assembly node(s)." + meshWarningSuffix(payload.warnings || []));
    }

    function updateMetadata(payload) {
        sourceStepFileEl.textContent = payload.sourceStepFile || "None";
        schemaNamesEl.textContent = (payload.schemaNames || []).join(", ") || "None";
        nodeCountEl.textContent = String(countNodes(payload.roots || []));
        selectedLabelEl.textContent = "None";
    }

    function renderWarnings(warnings) {
        warningListEl.innerHTML = "";
        const items = warnings.length > 0 ? warnings : ["No warnings."];
        items.forEach(function (warning) {
            const item = document.createElement("li");
            item.textContent = warning;
            warningListEl.appendChild(item);
        });
    }

    function renderTree(roots) {
        treeRootEl.innerHTML = "";
        if (roots.length === 0) {
            const empty = document.createElement("p");
            empty.className = "empty-state";
            empty.textContent = "No assembly roots were returned.";
            treeRootEl.appendChild(empty);
            return;
        }

        const list = document.createElement("ul");
        roots.forEach(function (node) {
            list.appendChild(buildTreeItem(node));
        });
        treeRootEl.appendChild(list);
    }

    function buildTreeItem(node) {
        const item = document.createElement("li");
        item.className = "tree-node";

        const button = document.createElement("button");
        button.type = "button";
        button.className = "tree-button";
        button.dataset.nodeId = node.instanceId;

        const title = document.createElement("span");
        title.className = "tree-title";
        title.textContent = node.displayName;
        button.appendChild(title);

        const meta = document.createElement("span");
        meta.className = "tree-meta";
        meta.textContent = "#" + node.definitionId + glbStatusLabel(node);
        button.appendChild(meta);

        button.addEventListener("click", function () {
            selectNode(node.instanceId);
        });

        item.appendChild(button);

        if (node.children && node.children.length > 0) {
            const childList = document.createElement("ul");
            node.children.forEach(function (child) {
                childList.appendChild(buildTreeItem(child));
            });
            item.appendChild(childList);
        }

        return item;
    }

    async function loadMeshesForScene(roots) {
        const nodes = flattenNodes(roots);
        for (const node of nodes) {
            if (!node.glb || !node.glb.exported || !node.glb.relativeUri) {
                continue;
            }
            try {
                const result = await BABYLON.SceneLoader.ImportMeshAsync("", "", node.glb.relativeUri, scene);
                const meshes = result.meshes.filter(function (mesh) {
                    return mesh && mesh.name !== "__root__";
                });
                meshes.forEach(function (mesh) {
                    mesh.metadata = Object.assign({}, mesh.metadata, {
                        assemblyNodeId: node.instanceId,
                        displayName: node.displayName
                    });
                    if (!mesh.material) {
                        const material = new BABYLON.StandardMaterial("material-" + node.instanceId, scene);
                        material.diffuseColor = BABYLON.Color3.FromHexString("#c49a6c");
                        mesh.material = material;
                    }
                });
                state.nodeMeshes.set(node.instanceId, meshes);
            } catch (error) {
                console.error("Failed to load GLB for node", node.instanceId, error);
            }
        }
    }

    function selectNode(nodeId) {
        state.activeNodeId = nodeId;
        const buttons = treeRootEl.querySelectorAll(".tree-button");
        buttons.forEach(function (button) {
            button.classList.toggle("active", button.dataset.nodeId === nodeId);
        });

        highlightLayer.removeAllMeshes();
        const selectedMeshes = state.nodeMeshes.get(nodeId) || [];
        const selectedSet = new Set(selectedMeshes);
        state.nodeMeshes.forEach(function (meshes, meshNodeId) {
            const visible = meshNodeId === nodeId || selectedMeshes.length === 0;
            meshes.forEach(function (mesh) {
                mesh.isVisible = visible;
            });
        });

        selectedMeshes.forEach(function (mesh) {
            mesh.isVisible = true;
            highlightLayer.addMesh(mesh, BABYLON.Color3.FromHexString("#0b6e4f"));
        });

        const node = findNodeById(state.currentSceneData ? state.currentSceneData.roots || [] : [], nodeId);
        selectedLabelEl.textContent = node ? node.displayName : "None";
        if (selectedSet.size > 0) {
            focusOnMeshes(selectedMeshes);
        } else {
            fitCameraToVisibleMeshes();
        }
    }

    function clearLoadedContent() {
        highlightLayer.removeAllMeshes();
        state.nodeMeshes.forEach(function (meshes) {
            meshes.forEach(function (mesh) {
                if (mesh && !mesh.isDisposed()) {
                    mesh.dispose(false, true);
                }
            });
        });
        state.nodeMeshes.clear();
        state.activeNodeId = null;
        selectedLabelEl.textContent = "None";
    }

    function fitCameraToVisibleMeshes() {
        const visibleMeshes = [];
        state.nodeMeshes.forEach(function (meshes) {
            meshes.forEach(function (mesh) {
                if (mesh.isVisible) {
                    visibleMeshes.push(mesh);
                }
            });
        });
        if (visibleMeshes.length === 0) {
            camera.setTarget(BABYLON.Vector3.Zero());
            camera.radius = 10;
            return;
        }
        focusOnMeshes(visibleMeshes);
    }

    function focusOnMeshes(meshes) {
        const min = new BABYLON.Vector3(Number.POSITIVE_INFINITY, Number.POSITIVE_INFINITY, Number.POSITIVE_INFINITY);
        const max = new BABYLON.Vector3(Number.NEGATIVE_INFINITY, Number.NEGATIVE_INFINITY, Number.NEGATIVE_INFINITY);

        meshes.forEach(function (mesh) {
            const info = mesh.getBoundingInfo();
            const boxMin = info.boundingBox.minimumWorld;
            const boxMax = info.boundingBox.maximumWorld;
            min.x = Math.min(min.x, boxMin.x);
            min.y = Math.min(min.y, boxMin.y);
            min.z = Math.min(min.z, boxMin.z);
            max.x = Math.max(max.x, boxMax.x);
            max.y = Math.max(max.y, boxMax.y);
            max.z = Math.max(max.z, boxMax.z);
        });

        const center = min.add(max).scale(0.5);
        const extent = max.subtract(min);
        const radius = Math.max(extent.length() * 0.8, 0.5);
        camera.setTarget(center);
        camera.radius = radius;
    }

    function flattenNodes(roots) {
        const nodes = [];
        roots.forEach(function walk(node) {
            nodes.push(node);
            (node.children || []).forEach(walk);
        });
        return nodes;
    }

    function findNodeById(roots, nodeId) {
        for (const node of roots) {
            if (node.instanceId === nodeId) {
                return node;
            }
            const child = findNodeById(node.children || [], nodeId);
            if (child) {
                return child;
            }
        }
        return null;
    }

    function countNodes(roots) {
        return flattenNodes(roots).length;
    }

    function glbStatusLabel(node) {
        if (!node.glb) {
            return " | no asset";
        }
        if (node.glb.exported) {
            return " | GLB ready";
        }
        if (node.glb.error && node.glb.error.indexOf("not configured") >= 0) {
            return " | GLB unavailable";
        }
        return " | GLB missing";
    }

    function meshWarningSuffix(warnings) {
        if (warnings.some(function (warning) {
            return warning.indexOf("GLB exporter is not configured") >= 0;
        })) {
            return " GLB preview is unavailable until STEP_PARSER_GLB_EXPORT_COMMAND is set.";
        }
        if (warnings.some(function (warning) {
            return warning.indexOf("built-in Java GLB exporter") >= 0;
        })) {
            return " Preview geometry is generated by the built-in Java exporter.";
        }
        return warnings.some(function (warning) {
            return warning.indexOf("GLB export failed") >= 0;
        }) ? " Some GLBs were not exported." : "";
    }

    function setStatus(message, isError) {
        statusEl.textContent = message;
        statusEl.style.color = isError ? "#a23b2a" : "";
    }
})();
