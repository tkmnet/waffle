class Executable < Java::jp.tkms.waffle.data.project.executable.Executable
end

class Computer < Java::jp.tkms.waffle.data.computer.Computer
end

class Conductor < Java::jp.tkms.waffle.data.project.conductor.Conductor
end

class ConductorArgument
    def initialize(entity)
        @entity = entity
    end

    def [](key)
        @entity.getArgument(key)
    end

    def set_prop(key, value)
        @entity.putArgument(key, value)
    end
end

#class Actor < Java::jp.tkms.waffle.data.project.workspace.run.ActorRun
#end

#class SimulatorRun < Java::jp.tkms.waffle.data.project.workspace.run.SimulatorRun
#end

class Registry < Java::jp.tkms.waffle.data.project.workspace.Registry
end

def self.system_restart()
    Java::jp.tkms.waffle.Main.restart()
end

def self.alert(text)
    Java::jp.tkms.waffle.data.log.message.DebugLogMessage.issue("alert: " + text.to_s);
end

def get_store(registry, entity_id)
    serialized_store = registry.get(".S:" + entity_id, "[]")
    if serialized_store == "[]" then
        store = Hash.new()
    else
        store = Marshal.load(serialized_store)
    end
end

def get_template_argument(registry, entity_id)
    serialized_template_argument = registry.get(".TA:" + entity_id, "[]")
    if serialized_template_argument == "[]" then
        template_argument = TemplateArgument.new()
    else
        template_argument = Marshal.load(serialized_template_argument)
    end
end

class TemplateArgument
    def initialize
        @p = Hash.new
        @f = Hash.new
    end

    def p
        @p
    end

    def f
        @f
    end
end

=begin
    class Hub < Java::jp.tkms.waffle.data.util.Hub
        def initialize(conductorRun, run, template)
            super(conductorRun, run, template)
            @store = get_store(registry, conductorRun.id)
            @template_argument =  get_template_argument(registry, conductorRun.id)
        end

        def close
        #TODO: check with depth
            registry.set(".S:" + conductorRun.id, Marshal.dump(@store))
            registry.set(".TA:" + conductorRun.id, Marshal.dump(@template_argument))
            super
            registry.set(".S:" + conductorRun.id, Marshal.dump(@store))
            registry.set(".TA:" + conductorRun.id, Marshal.dump(@template_argument))
        end

        def loadConductorTemplate(name)
            super
            @template_argument
        end

        def loadListenerTemplate(name)
            super
            @template_argument
        end

        def p
            @template_argument.p
        end

        def f
            @template_argument.f
        end
    end

    def exec_process(conductorRun, run, &block)
        result = true
        hub = Hub.new(conductorRun, run, nil)
        result = block.call(hub)
        hub.close
        return result
    end

    def exec_conductor_script(conductorRun)
        exec_process conductorRun, conductorRun do | hub |
            next conductor_script(hub, conductorRun)
        end
    end

    def exec_listener_script(conductorRun, run)
        exec_process conductorRun, run do | hub |
            next listener_script(hub, run)
        end
    end

    def exec_template_process(conductorRun, run, template, &block)
        result = true
        hub = Hub.new(conductorRun, run, template)
        result = block.call(hub)
        hub.close
        return result
    end

    def exec_conductor_template_script(conductorRun, conductorTemplate)
        exec_template_process conductorRun, conductorRun, conductorTemplate do | hub |
            next conductor_script(hub, conductorRun)
        end
    end

    def exec_listener_template_script(conductorRun, run)
        exec_template_process conductorRun, run, nil do | hub |
            next listener_script(hub, run)
        end
    end
=end

def parameter_extract(run)
end

def exec_parameter_extract(run)
    Dir.chdir(run.getBasePath().toString()) do
        parameter_extract(run)
    end
end
#module_function :exec_parameter_extract

def result_collect(run, remote)
end

def exec_result_collect(run, remote)
    Dir.chdir(run.getBasePath().toString()) do
        result_collect(run, remote)
    end
end
#module_function :exec_result_collect

class ActorWrapper
    def initialize(actorRun)
        @instance = actorRun
        @store = get_store(@instance.getRegistry, @instance.id)
        @template_argument =  get_template_argument(@instance.getRegistry, @instance.id)
    end

    def close
    #TODO: check with depth
        @instance.getRegistry.set(".S:" + @instance.getId, Marshal.dump(@store))
        @instance.getRegistry.set(".TA:" + @instance.getId, Marshal.dump(@template_argument))
        @instance.commit
        @instance.getRegistry.set(".S:" + @instance.getId, Marshal.dump(@store))
        @instance.getRegistry.set(".TA:" + @instance.getId, Marshal.dump(@template_argument))
    end

    def id
        @instance.id
    end

    def createConductorRun(conductor_name, name)
        @instance.createConductorRun(conductor_name, name)
    end

    def createConductorRun(conductor_name)
        @instance.createConductorRun(conductor_name, conductor_name)
    end

    def createExecutableRun(executable_name, computer_name, name)
        @instance.createExecutableRun(executable_name, computer_name, name)
    end

    def createExecutableRun(executable_name, computer_name)
        @instance.createExecutableRun(executable_name, computer_name, executable_name)
    end

    def addFinalizer(name)
        @instance.addFinalizer(name)
    end

    def v
        @instance.v
    end

    def loadConductorTemplate(name)
        super
        @template_argument
    end

    def loadListenerTemplate(name)
        super
        @template_argument
    end

    def p
        @template_argument.p
    end

    def f
        @template_argument.f
    end
end

def exec_procedure_when_start_or_finished_all(instance, caller)
    result = true
    local_instance = ActorWrapper.new(instance)
    result = procedure_when_start_or_finished_all(local_instance, caller)
    local_instance.close
    return result
end

def exec_procedure_when_contain_fault(instance, caller)
    result = true
    local_instance = ActorWrapper.new(instance)
    result = procedure_when_contain_fault(local_instance, caller)
    local_instance.close
    return result
end

def exec_procedure_when_appealed(instance, caller)
    result = true
    local_instance = ActorWrapper.new(instance)
    result = procedure_when_appealed(local_instance, caller)
    local_instance.close
    return result
end


class Java::JavaLang::Object
    def is_group?()
        return self.is_a?(Java::JavaUtil::Map)
    end
end

class Numeric
    def is_group?()
        return false
    end

    def to_str
        return to_s
    end
end
