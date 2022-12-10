class FlowInterface extends BaklavaJS.Core.NodeInterface {
  constructor() {
    super("flow");
    this.use(BaklavaJS.InterfaceTypes.setType, "flow");
  }
  checkOutput(to, prevent, graph) {
    if (this.connectionCount > 0) {
      prevent();
    }
    if (!(to instanceof FlowInterface)) {
      prevent();
    }
  }
  checkInput(from, prevent, graph) {
    if (this.connectionCount > 0) {
      prevent();
    }
    if (!(from instanceof FlowInterface)) {
      prevent();
    }
  }
};

class ValueInterface extends BaklavaJS.Core.NodeInterface {
  constructor(name) {
    super((name == undefined ? "value" : name));
    this.use(BaklavaJS.InterfaceTypes.setType, "value");
  }
  checkOutput(to, prevent, graph) {
  }
  checkInput(from, prevent, graph) {
    if (this.connectionCount > 0) {
      prevent();
    }
    if (!(from instanceof ValueInterface)) {
      prevent();
    }
  }
};

class NamedValueInterface extends ValueInterface {
  constructor(name) {
    super((name == undefined ? "value" : name));
    this.use(BaklavaJS.InterfaceTypes.setType, "value");
  }
  checkOutput(to, prevent, graph) {
  }
};

class ValueSetInterface extends ValueInterface {
  constructor(name) {
    super((name == undefined ? "values" : name));
    this.use(BaklavaJS.InterfaceTypes.setType, "values");
  }
  checkOutput(to, prevent, graph) {
  }
  checkInput(from, prevent, graph) {
    if (!(from instanceof NamedValueInterface)) {
      prevent();
    }
  }
};

class ValueArrayInterface extends ValueInterface {
  constructor(name) {
    super((name == undefined ? "values" : name));
    this.use(BaklavaJS.InterfaceTypes.setType, "values");
  }
  checkOutput(to, prevent, graph) {
  }
  checkInput(from, prevent, graph) {
    if (!(from instanceof ValueInterface)) {
      prevent();
    }
  }
};

class RunInterface extends BaklavaJS.Core.NodeInterface {
  constructor(name) {
    super((name == undefined ? "run" : name));
    this.use(BaklavaJS.InterfaceTypes.setType, "run");
  }
  checkOutput(to, prevent, graph) {
  }
  checkInput(from, prevent, graph) {
    if (!(from instanceof RunInterface)) {
      prevent();
    }
  }
};

class NamedRunInterface extends RunInterface {
  constructor(name) {
    super((name == undefined ? "run" : name));
    this.use(BaklavaJS.InterfaceTypes.setType, "run");
  }
  checkOutput(to, prevent, graph) {
  }
};

class RunSetInterface extends RunInterface {
  constructor(name) {
    super((name == undefined ? "runs" : name));
    this.use(BaklavaJS.InterfaceTypes.setType, "runs");
  }
  checkOutput(to, prevent, graph) {
  }
  checkInput(from, prevent, graph) {
    if (!(from instanceof NamedRunInterface)) {
      prevent();
    }
  }
};

class RunArrayInterface extends RunInterface {
  constructor(name) {
    super((name == undefined ? "runs" : name));
    this.use(BaklavaJS.InterfaceTypes.setType, "runs");
  }
  checkOutput(to, prevent, graph) {
  }
  checkInput(from, prevent, graph) {
    if (!(from instanceof RunInterface)) {
      prevent();
    }
  }
};

class NameInterface extends BaklavaJS.RendererVue.TextInputInterface {
  constructor() {
    super("name");
    this.use(BaklavaJS.InterfaceTypes.setType, "name");
  }
  checkInput(from, prevent, graph) {
    if (this.connectionCount > 0) {
      prevent();
    } else if (!(from instanceof ValueInterface)) {
      prevent();
    } else if (from instanceof ValueSetInterface || from instanceof ValueArrayInterface) {
      prevent();
    }
  }
};

class RunOrValueInterface extends BaklavaJS.Core.NodeInterface {
  constructor(name) {
    super((name == undefined ? "run/value" : name));
    this.use(BaklavaJS.InterfaceTypes.setType, "runvalue");
  }
  checkOutput(to, prevent, graph) {
  }
  checkInput(from, prevent, graph) {
    if (!(from instanceof RunInterface)) {
      prevent();
    }
  }
};

class RunOrValueInputInterface extends BaklavaJS.RendererVue.TextInputInterface {
  constructor() {
    super("value");
    this.use(BaklavaJS.InterfaceTypes.setType, "value");
  }
  checkInput(from, prevent, graph) {
    if (this.connectionCount > 0) {
      prevent();
    } else if (!(from instanceof ValueInterface)) {
      prevent();
    }
  }
};

class ComputerInterface extends BaklavaJS.RendererVue.TextInputInterface {
  constructor() {
    super("computer");
    this.use(BaklavaJS.InterfaceTypes.setType, "computer");
  }
  checkInput(from, prevent, graph) {
    if (this.connectionCount > 0) {
      prevent();
    }
    if (!(from instanceof ValueInterface)) {
      prevent();
    } else if (from instanceof ValueSetInterface || from instanceof ValueArrayInterface) {
      prevent();
    }
  }
};

class ProcedureInterface extends BaklavaJS.RendererVue.TextInputInterface {
  constructor() {
    super("procedure");
    this.use(BaklavaJS.InterfaceTypes.setType, "procedure");
  }
  checkInput(from, prevent, graph) {
    if (this.connectionCount > 0) {
      prevent();
    }
    if (!(from instanceof ValueInterface)) {
      prevent();
    } else if (from instanceof ValueSetInterface || from instanceof ValueArrayInterface) {
      prevent();
    }
  }
};

class ExecutableInterface extends BaklavaJS.RendererVue.TextInputInterface {
  constructor() {
    super("executable");
    this.use(BaklavaJS.InterfaceTypes.setType, "executable");
  }
  checkInput(from, prevent, graph) {
    if (this.connectionCount > 0) {
      prevent();
    }
    if (!(from instanceof ValueInterface)) {
      prevent();
    } else if (from instanceof ValueSetInterface || from instanceof ValueArrayInterface) {
      prevent();
    }
  }
};

class ConductorInterface extends BaklavaJS.RendererVue.TextInputInterface {
  constructor() {
    super("conductor");
    this.use(BaklavaJS.InterfaceTypes.setType, "conductor");
  }
  checkInput(from, prevent, graph) {
    if (this.connectionCount > 0) {
      prevent();
    }
    if (!(from instanceof ValueInterface)) {
      prevent();
    } else if (from instanceof ValueSetInterface || from instanceof ValueArrayInterface) {
      prevent();
    }
  }
};

var nodeEditorNodes = [
  BaklavaJS.Core.defineNode({
    type: "ProcedureRun",
    inputs: {
      flow: () => new FlowInterface(),
      procedure: () => new ProcedureInterface(),
      guard: () => new RunArrayInterface("guard"),
      referable: () => new RunArrayInterface("referable"),
    },
    outputs: {
      flow: () => new FlowInterface(),
    },
  }),
  BaklavaJS.Core.defineNode({
    type: "ExecutableRun",
    inputs: {
      flow: () => new FlowInterface(),
      executable: () => new ExecutableInterface(),
      computer: () => new ComputerInterface(),
      parameters: () => new ValueSetInterface("parameters"),
    },
    outputs: {
      flow: () => new FlowInterface(),
      reference: () => new RunInterface(),
    },
  }),
  BaklavaJS.Core.defineNode({
    type: "ConductorRun",
    inputs: {
      flow: () => new FlowInterface(),
      conductor: () => new ConductorInterface(),
      variables: () => new ValueSetInterface("variables"),
    },
    outputs: {
      flow: () => new FlowInterface(),
      reference: () => new RunInterface(),
    },
  }),
  BaklavaJS.Core.defineNode({
    type: "NewRunArray",
    inputs: {
      flow: () => new FlowInterface(),
      runs: () => new RunArrayInterface("runs"),
    },
    outputs: {
      flow: () => new FlowInterface(),
      array: () => new RunArrayInterface("array"),
    },
  }),
  BaklavaJS.Core.defineNode({
    type: "NewArray",
    inputs: {
      flow: () => new FlowInterface(),
      values: () => new ValueArrayInterface("values"),
    },
    outputs: {
      flow: () => new FlowInterface(),
      array: () => new ValueArrayInterface("array"),
    },
  }),
  BaklavaJS.Core.defineNode({
    type: "GetVariable",
    inputs: {
      name: () => new NameInterface(),
    },
    outputs: {
      value: () => new NamedValueInterface(),
    },
  }),
  BaklavaJS.Core.defineNode({
    type: "SetVariable",
    inputs: {
      name: () => new NameInterface(),
      value: () => new ValueInterface(),
    },
    outputs: {
    },
  }),
  BaklavaJS.Core.defineNode({
    type: "WithName",
    inputs: {
      name: () => new NameInterface(),
      value: () => new RunOrValueInputInterface(),
    },
    outputs: {
      value: () => new NamedRunOrValueInterface(),
    },
  }),
  BaklavaJS.Core.defineNode({
    type: "Begin",
    inputs: {},
    outputs: {
      flow: () => new FlowInterface(),
    },
  }),
];


$(function() {
  Array.from(document.getElementsByClassName("node-editor")).forEach(editorArea => {
    var viewModel = BaklavaJS.createBaklava(editorArea);
    nodeEditorNodes.forEach(node => viewModel.editor.registerNodeType(node));
    var data = JSON.parse(editorArea.nextElementSibling.innerHTML);
    var adjustedHeight = 500;
    data.graph.nodes.forEach(node => {
      height = 250 + node.position.y;
      adjustedHeight = (height > adjustedHeight ? height : adjustedHeight);
    });
    editorArea.style.height = adjustedHeight + "px";
    viewModel.editor.load(data);

    let update = function() {
      editorArea.nextElementSibling.innerHTML = JSON.stringify(viewModel.editor.save());
    };
    viewModel.editor.graphEvents.addNode.subscribe(update, update);
    viewModel.editor.graphEvents.removeNode.subscribe(update, update);
    viewModel.editor.graphEvents.addConnection.subscribe(update, update);
    viewModel.editor.graphEvents.removeConnection.subscribe(update, update);
    viewModel.editor.nodeEvents.update.subscribe(update, update);
    editorArea.addEventListener("mouseout", update);

    let check = function(connection, prevent, graph) {
      debugCon = connection;
      if (connection.from.checkOutput != undefined && connection.to.checkInput != undefined) {
        connection.from.checkOutput(connection.to, prevent, graph);
        connection.to.checkInput(connection.from, prevent, graph);
      } else {
        prevent();
      }
    }
    viewModel.editor.graphEvents.checkConnection.subscribe(check, check);
  });
});
