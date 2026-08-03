var ASMAPI = Java.type('net.minecraftforge.coremod.api.ASMAPI');
var Opcodes = Java.type('org.objectweb.asm.Opcodes');

function initializeCoreMod() {
    return {
        'meplacementtool_after_recipe_filter_init': {
            'target': {
                'type': 'METHOD',
                'class': 'com.gtocore.data.Data',
                'methodName': 'commonInit',
                'methodDesc': '()V'
            },
            'transformer': function(method) {
                var nodes = method.instructions.toArray();
                var injected = 0;
                for (var i = 0; i < nodes.length; i++) {
                    var node = nodes[i];
                    if (node.getOpcode() === Opcodes.INVOKESTATIC &&
                        node.owner === 'com/gtocore/data/recipe/RecipeFilter' &&
                        node.name === 'init' &&
                        node.desc === '()V') {
                        method.instructions.insert(node, ASMAPI.buildMethodCall(
                            'com/moakiee/meplacementtool/recipe/PlacementToolRecipeRegistration',
                            'register',
                            '()V',
                            ASMAPI.MethodType.STATIC
                        ));
                        injected++;
                    }
                }
                if (injected !== 1) {
                    throw new Error('ME Placement Tool expected one RecipeFilter.init() call, found ' + injected);
                }
                ASMAPI.log('INFO', 'ME Placement Tool injected five crafting recipes after RecipeFilter.init()');
                return method;
            }
        }
    };
}
